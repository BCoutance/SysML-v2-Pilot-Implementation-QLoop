/*******************************************************************************
 * SysML 2 Pilot Implementation
 * Copyright (c) 2022-2026 Model Driven Solutions, Inc.
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

package org.omg.sysml.execution.expressions;

import org.eclipse.emf.common.util.EList;
import org.omg.sysml.expressions.ModelLevelExpressionEvaluator;
import org.omg.sysml.expressions.functions.LibraryFunction;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureTyping;
import org.omg.sysml.lang.sysml.FeatureValue;
import org.omg.sysml.lang.sysml.Function;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.Membership;
import org.omg.sysml.lang.sysml.Redefinition;
import org.omg.sysml.lang.sysml.Specialization;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.Type;
import org.omg.sysml.util.ElementUtil;
import org.omg.sysml.util.EvaluationUtil;
import org.omg.sysml.util.FeatureUtil;
import org.omg.sysml.util.NamespaceUtil;
import org.omg.sysml.util.TypeUtil;

public class ExpressionEvaluator extends ModelLevelExpressionEvaluator {
	
	public static final ExpressionEvaluator INSTANCE = new ExpressionEvaluator();
	
	public ExpressionEvaluator() {
		super();
		setLibraryFunctionFactory(new LibraryFunctionFactory());
	}
	
	@Override
	public EList<Element> evaluateInvocation(InvocationExpression expression, Element target) {
		Function function = expression.getFunction();		
		// Considering that isModelLevelEvaluable() should be inherited through specialization (true?), and since it is not applied
		// when creating the resource, we search for ModelLevelEvaluable functions in the specialized functions (until we are not
		// specializing functions anymore or we found one) and replace the function by the first ModelLevelEvaluable one found if 
		// it exists. Beware of the disappearing of redefinitions during specialization in this process. Better solution to be found...
		if (function != null) {
			// Why does sometimes the getFunction() result is Evaluation instead of the real function ? 
			// To avoid it, if Evaluation is caught, always check if there is not another function in the memberships
			// Beware of the risk of false positives (case where Evaluation is the correct function and not the other one)
			if (function.getQualifiedName()!= null && function.getQualifiedName().equals("Performances::Evaluation")) {
				if (expression.instantiatedType() instanceof Function typefunc) {
					expression.getType().remove(function);
					expression.getType().add(typefunc);
					function = typefunc;
				}
				else {
					for (Membership memb : expression.getOwnedMembership()) {
						if (memb.getTarget().get(0) instanceof Function tgtfunc) {
							expression.getType().remove(function);
							expression.getType().add(tgtfunc);
							function = tgtfunc;
						}
					}
				}
			}
			LibraryFunction libraryFunction = libraryFunctionFactory.getLibraryFunction(function);

			EList<Specialization> FuncSpecialization = function.getOwnedSpecialization();
			while (FuncSpecialization.size()>0 && libraryFunction==null) {
				if (FuncSpecialization.get(0).getTarget().get(0) instanceof Function f) {
					libraryFunction = libraryFunctionFactory.getLibraryFunction(f);
					FuncSpecialization = f.getOwnedSpecialization();
				}
				else break;
			}
			if (libraryFunction != null) {
				return libraryFunction.invoke(expression, target, this);
			}
		}
		 
		Type type = expression.instantiatedType(); // New version, creates issues with collect select and reduce
		if (type == null) type = expression.getOwnedTyping().stream().map(FeatureTyping::getType).findFirst().orElse(null);
		Expression resultExpression = EvaluationUtil.getResultExpressionFor(type);
		if (resultExpression == null) {
			return EvaluationUtil.singletonList(expression);
		} else {
			Feature targetFeature = EvaluationUtil.getTargetFeatureFor(target);
			if (type instanceof Feature) {
				targetFeature = FeatureUtil.chainFeatures(targetFeature, (Feature)type);
			}
			InvocationExpression instantiatedInvocation = instantiateInvocation(expression, targetFeature);
			Feature chainedFeatures = FeatureUtil.chainFeatures(targetFeature, instantiatedInvocation);
			EList<Element> results = evaluate(resultExpression, chainedFeatures);
			return results == null? EvaluationUtil.singletonList(resultExpression): results;
		}	
	}
	
	protected InvocationExpression instantiateInvocation(InvocationExpression expression, Element target) {
		InvocationExpression instantiation = SysMLFactory.eINSTANCE.createInvocationExpression();
		
		// Copy instantiatedType from original expression.
		Type instantiatedType = expression.getInstantiatedType();
		if (instantiatedType==null) {
			instantiatedType = expression.getOwnedTyping().stream().map(FeatureTyping::getType).findFirst().orElse(null); //still necessary ?
		}
		NamespaceUtil.addMemberTo(instantiation, instantiatedType);
		
		// Addition to keep track of the original expression's function. Useful for later search for bound 
		// features in EvaluateFeature for example  
		Function function = expression.getFunction();
		if (function != null) {
			FeatureTyping newTyping = SysMLFactory.eINSTANCE.createFeatureTyping();
			newTyping.setType(function);
			newTyping.setTypedFeature(instantiation);
			instantiation.getOwnedRelationship().add(newTyping);
		}
		// Add implicit generalization.
		ElementUtil.transform(instantiation);
		
		// Evaluate value Expressions for parameters on original instantiation.
		for (Feature parameter: TypeUtil.getOwnedParametersOf(expression)) {
			Expression valueExpression = FeatureUtil.getValueExpressionFor(parameter);
			if (valueExpression != null) {
				// Add a new parameter to hold the result of the Expression evaluation.
				Feature newParameter = SysMLFactory.eINSTANCE.createFeature();
				TypeUtil.addOwnedFeatureTo(instantiation, newParameter);
				
				newParameter.setDirection(parameter.getDirection());
				for (Feature redefinedFeature: FeatureUtil.getRedefinedFeaturesWithComputedOf(parameter)) {
					Redefinition newRedefinition = SysMLFactory.eINSTANCE.createRedefinition();
					newRedefinition.setRedefinedFeature(redefinedFeature);
					newRedefinition.setRedefiningFeature(newParameter);
					newParameter.getOwnedRelationship().add(newRedefinition);
				}				

				// Evaluate the value expression for the original parameter with the given target,
				// NOT including the bindings in the original invocation expression.
				EList<Element> values = evaluate(valueExpression, target);
				if (values != null) {
					// Set the value expression for the new parameter to an expression representing
					// the evaluated result of the original value expression.
					Expression evaluatedExpression = EvaluationUtil.expressionFor(values, expression);
					if (evaluatedExpression != null) {
						FeatureValue newFeatureValue = SysMLFactory.eINSTANCE.createFeatureValue();
						newFeatureValue.setValue(evaluatedExpression);
						newParameter.getOwnedRelationship().add(newFeatureValue);
					}
				}
			}
		}
		
		return instantiation;
	}
	
	@Override
	public EList<Element> evaluateFeature(Feature feature, Type type) {
		EList<Element> results = super.evaluateFeature(feature, type);
		Element result = results == null || results.size() != 1? null: results.get(0);
		
		// Treat an unbound input parameter as if it was null.
		return type != null && result instanceof Feature && !(result instanceof Expression) &&
			   FeatureUtil.isInputParameter((Feature)results.get(0), type)? EvaluationUtil.nullList(): results;
	}
	
}
