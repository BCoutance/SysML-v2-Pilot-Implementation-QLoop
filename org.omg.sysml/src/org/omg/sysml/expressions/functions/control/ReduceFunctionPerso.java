/*******************************************************************************
 * SysML 2 Pilot Implementation
 * Copyright (c) 2022 Model Driven Solutions, Inc.
 *    
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  
 * @license LGPL-3.0-or-later <http://spdx.org/licenses/LGPL-3.0-or-later>
 *  
 *******************************************************************************/

package org.omg.sysml.expressions.functions.control;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.omg.sysml.expressions.ExpressionEvaluator;
import org.omg.sysml.expressions.functions.LibraryFunction;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.FeatureReferenceExpression;
import org.omg.sysml.lang.sysml.FeatureTyping;
import org.omg.sysml.lang.sysml.FeatureValue;
import org.omg.sysml.lang.sysml.Function;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.Membership;
import org.omg.sysml.lang.sysml.ResultExpressionMembership;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.util.EvaluationUtil;
import org.omg.sysml.util.TypeUtil;


public class ReduceFunctionPerso implements LibraryFunction {

	@Override
	public String getPackageName() {
		return "ControlFunctions";
	}

	@Override
	public String getFunctionName() {
		return "reduce";
	}
	
	// String tokenText = NodeModelUtils.getTokenText(NodeModelUtils.getNode(...));
	@Override
	public EList<Element> invoke(InvocationExpression invocation, Element target, ExpressionEvaluator evaluator) {
		EList<Element> result = new BasicEList<>();

		EList<Element> collection = evaluator.evaluateArgument(invocation, 0, target);
		// TODO when input is a collection of collections like ((1,2,3), (10,20,30), (100,200,300))
		// evaluate argument return a flat list of the elements values like (1,2,3,10,20,30,100,200,300).
		
		if (collection != null) {				
			if (collection.size() > 1) {	
				Expression reducer = (Expression) ((FeatureReferenceExpression)invocation.getArgument().get(1)).getReferent();
				// if the reducer is an already defined function, we use it instead of creating a new function
				// Evaluation is the most generic type of function, if we catch it it means we have a generic expression 
				Function func = null;
				Boolean isGenericExpr = reducer.getType().get(0).getQualifiedName().equals("Performances::Evaluation");
				if (!isGenericExpr) func = (Function) reducer.getType().get(0);
				else func = createFunction(reducer);
				
				result.add(collection.get(0));
				for (int i = 1; i < collection.size(); i++) {
					Element element = collection.get(i);
					if (element instanceof Feature elementFeature) {					
						InvocationExpression invoc = createInvocation((Feature) result.get(0), func, elementFeature);
						// Evaluate this invocation to update the result
						result = evaluator.evaluateInvocation(invoc, target); 
					}
				}
				if (isGenericExpr) resetReducerFeatures(reducer, func);
				return result;
			}
		}
		return EvaluationUtil.singletonList(invocation);
	}

	private Function createFunction(Expression reducer) {
		// Build a new function based on the reducer expression that borrows its FeaturesMemberships
		// Since the reducer is not elsewhere defined, the reducer will not be accessed before another call of this reduce invoke
		// The FeaturesMemberships are returned at the end of the invoke call
		Function func = SysMLFactory.eINSTANCE.createFunction();
		
		FeatureMembership fm0 = reducer.getOwnedFeatureMembership().get(0);
		FeatureMembership newfm0 = SysMLFactory.eINSTANCE.createFeatureMembership();
		newfm0.setOwnedMemberFeature(fm0.getOwnedMemberFeature());
		newfm0.setOwningType(func);
		func.getOwnedRelationship().add(newfm0);
		
		FeatureMembership fm1 = reducer.getOwnedFeatureMembership().get(1);
		FeatureMembership newfm1 = SysMLFactory.eINSTANCE.createFeatureMembership();
		newfm1.setOwnedMemberFeature(fm1.getOwnedMemberFeature());
		newfm1.setOwningType(func);
		func.getOwnedRelationship().add(newfm1);
		
		ResultExpressionMembership fm2 = (ResultExpressionMembership) reducer.getOwnedFeatureMembership().get(2);
		ResultExpressionMembership newfm2 = SysMLFactory.eINSTANCE.createResultExpressionMembership();
		newfm2.setOwnedMemberFeature(fm2.getOwnedMemberFeature());
		newfm2.setOwningType(func);
		func.getOwnedRelationship().add(newfm2);
		
		reducer.eResource().getContents().add(func);
		
		return func;
	}
	
	private InvocationExpression createInvocation(Feature result, Function func, Feature elementFeature) {
		// Build a new invocation of the function whose parameters are : 
		// - the accumulator whose valueExpression evaluates as a the previous value of the result
		// - one whose valueExpression evaluates as a reference to the studied element of the collection. 
		// Can we do something simpler ?
		InvocationExpression invoc = SysMLFactory.eINSTANCE.createInvocationExpression();
		
		FeatureTyping newTyping = SysMLFactory.eINSTANCE.createFeatureTyping();
		newTyping.setType(func);
		newTyping.setTypedFeature(invoc);
		invoc.getOwnedRelationship().add(newTyping);
		
		// create accumulator outside of the method ?
		Feature accumulator = SysMLFactory.eINSTANCE.createFeature();
		FeatureValue accumulatorFV = SysMLFactory.eINSTANCE.createFeatureValue();
		FeatureReferenceExpression accumulatorExpr = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
		Membership accumulatorReferentMembership = SysMLFactory.eINSTANCE.createMembership();

		TypeUtil.addOwnedFeatureTo(invoc, accumulator);
		accumulator.setDirection(FeatureDirectionKind.IN);
		accumulator.getOwnedRelationship().add(accumulatorFV);
		accumulatorFV.getOwnedRelatedElement().add(accumulatorExpr);
		accumulatorExpr.getOwnedRelationship().add(accumulatorReferentMembership);
		accumulatorReferentMembership.setMemberElement(result);
				
		Feature parameter1 = SysMLFactory.eINSTANCE.createFeature();
		FeatureValue parameterFV1 = SysMLFactory.eINSTANCE.createFeatureValue();
		FeatureReferenceExpression elementExpr1 = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
		Membership referentmembership1 = SysMLFactory.eINSTANCE.createMembership();
		
		TypeUtil.addOwnedFeatureTo(invoc, parameter1);
		parameter1.setDirection(FeatureDirectionKind.IN);
		parameter1.getOwnedRelationship().add(parameterFV1);
		parameterFV1.getOwnedRelatedElement().add(elementExpr1);
		elementExpr1.getOwnedRelationship().add(referentmembership1);
		referentmembership1.setMemberElement(elementFeature);

		return invoc;
	}
	
	private void resetReducerFeatures(Expression reducer, Function func) {
		// return the FeaturesMemberships to the reducer
		EList<FeatureMembership> funcfm = func.getOwnedFeatureMembership();	
		EList<FeatureMembership> reducerfm = reducer.getOwnedFeatureMembership();
		
		if (reducerfm != null && funcfm != null && reducerfm.size()==funcfm.size()) {
			for (int i = 0; i < reducerfm.size(); i++) {
				reducerfm.get(i).setOwnedMemberFeature(funcfm.get(i).getOwnedMemberFeature());
			}
		}
		reducer.eResource().getContents().remove(func);
	}
}