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

public class CollectFunctionPerso implements LibraryFunction {

	@Override
	public String getPackageName() {
		return "ControlFunctions";
	}

	@Override
	public String getFunctionName() {
		return "collect";
	}
	
	// String tokenText = NodeModelUtils.getTokenText(NodeModelUtils.getNode(...));
	@Override
	public EList<Element> invoke(InvocationExpression invocation, Element target, ExpressionEvaluator evaluator) {
		EList<Element> result = new BasicEList<>();

		EList<Element> collection = evaluator.evaluateArgument(invocation, 0, target);
		// TODO when input is a collection of collections like ((1,2,3), (10,20,30), (100,200,300))
		// evaluate argument return a flat list of the elements values like (1,2,3,10,20,30,100,200,300).
		
		if (collection != null) {		
			Expression mapper = (Expression) ((FeatureReferenceExpression)invocation.getArgument().get(1)).getReferent();
			// if the mapper is an already defined function, we use it instead of creating a new function
			// Performances::Evaluation is the most generic type of function, if we catch it it means we have a generic expression 
			Function func = null;
			Boolean isGenericExpr = mapper.getType().get(0).getQualifiedName().equals("Performances::Evaluation");
			if (!isGenericExpr) func = (Function) mapper.getType().get(0);
			else func = createFunction(mapper);
						
			for (Element element : collection) {
				if (element instanceof Feature elementFeature) {					
					InvocationExpression invoc = createInvocation(func, elementFeature);
					// Evaluate this invocation to add the result to the collected results
					EList<Element> values = evaluator.evaluateInvocation(invoc, target); 
					for (Element value : values) result.add(value);
				}
			}
			if (isGenericExpr) resetMapperFeatures(mapper, func);
			return result;
		}
		return EvaluationUtil.singletonList(invocation);
			
	}
	
	private Function createFunction(Expression mapper) {
		// Build a new function based on the mapper expression that copies its FeaturesMemberships
		// The Features are stolen from the mapper, but since the mapper is not elsewhere defined, the mapper will not be accessed before another call of this collect invoke
		// The Features are returned at the end of the invoke call
		Function func = SysMLFactory.eINSTANCE.createFunction();

		// Is it better this way (with created new fm) or the select way (with borrowed fm) ?

		FeatureMembership fm0 = mapper.getOwnedFeatureMembership().get(0);
		FeatureMembership newfm0 = SysMLFactory.eINSTANCE.createFeatureMembership();
		newfm0.setOwnedMemberFeature(fm0.getOwnedMemberFeature());
		newfm0.setOwningType(func);
		func.getOwnedRelationship().add(newfm0);

		FeatureMembership fm1 = mapper.getOwnedFeatureMembership().get(1);
		ResultExpressionMembership newfm1 = SysMLFactory.eINSTANCE.createResultExpressionMembership();
		
		Feature resultMember = fm1.getOwnedMemberFeature();
		if (!(resultMember instanceof Expression)) resultMember = EvaluationUtil.expressionFor(resultMember);
		// TODO make a "constructor" (in invocation)
		newfm1.setOwnedMemberFeature(resultMember);
		newfm1.setOwningType(func);
		func.getOwnedRelationship().add(newfm1);

		mapper.eResource().getContents().add(func);

		return func;
	}

	private InvocationExpression createInvocation(Function func, Feature elementFeature) {
		// Build a new invocation of the function whose parameter has a valueExpression that evaluates as 
		// a reference to the studied element of the collection. 
		// Can we do something simpler ?
		InvocationExpression invoc = SysMLFactory.eINSTANCE.createInvocationExpression();
		
		FeatureTyping newTyping = SysMLFactory.eINSTANCE.createFeatureTyping();
		newTyping.setType(func);
		newTyping.setTypedFeature(invoc);
		invoc.getOwnedRelationship().add(newTyping);
		
		Feature parameter = SysMLFactory.eINSTANCE.createFeature();
		FeatureValue parameterFV = SysMLFactory.eINSTANCE.createFeatureValue();
		FeatureReferenceExpression elementExpr = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
		Membership referentmembership = SysMLFactory.eINSTANCE.createMembership();
		
		TypeUtil.addOwnedFeatureTo(invoc, parameter);
		parameter.setDirection(FeatureDirectionKind.IN);
		parameter.getOwnedRelationship().add(parameterFV);
		parameterFV.getOwnedRelatedElement().add(elementExpr);
		elementExpr.getOwnedRelationship().add(referentmembership);
		referentmembership.setMemberElement(elementFeature);
				
		return invoc;
	}
	
	private void resetMapperFeatures(Expression mapper, Function func) {
		// return the Features to the mapper's FeaturesMemberships
		EList<FeatureMembership> mapperfm = mapper.getOwnedFeatureMembership();
		EList<FeatureMembership> funcfm = func.getOwnedFeatureMembership();	
		if (mapperfm != null && funcfm != null && mapperfm.size()==funcfm.size()) {
			for (int i = 0; i < mapperfm.size(); i++) {
				mapperfm.get(i).setOwnedMemberFeature(funcfm.get(i).getOwnedMemberFeature());
			}
		}
		mapper.eResource().getContents().remove(func);
	}	
}