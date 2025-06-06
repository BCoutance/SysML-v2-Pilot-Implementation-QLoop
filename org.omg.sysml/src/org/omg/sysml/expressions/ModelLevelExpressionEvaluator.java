/*******************************************************************************
 * SysML 2 Pilot Implementation
 * Copyright (c) 2022, 2025-2026 Model Driven Solutions, Inc.
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

package org.omg.sysml.expressions;

import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.omg.sysml.expressions.functions.LibraryFeature;
import org.omg.sysml.expressions.functions.LibraryFunction;
import org.omg.sysml.lang.sysml.AnnotatingElement;
import org.omg.sysml.lang.sysml.ConstructorExpression;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureReferenceExpression;
import org.omg.sysml.lang.sysml.FeatureTyping;
import org.omg.sysml.lang.sysml.Function;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.LiteralExpression;
import org.omg.sysml.lang.sysml.Membership;
import org.omg.sysml.lang.sysml.MetadataAccessExpression;
import org.omg.sysml.lang.sysml.MetadataFeature;
import org.omg.sysml.lang.sysml.NullExpression;
import org.omg.sysml.lang.sysml.Specialization;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.Type;
import org.omg.sysml.util.ElementUtil;
import org.omg.sysml.util.EvaluationUtil;
import org.omg.sysml.util.ExpressionUtil;
import org.omg.sysml.util.FeatureUtil;
import org.omg.sysml.util.TypeUtil;

public class ModelLevelExpressionEvaluator implements ExpressionEvaluator {
	
	public static final ModelLevelExpressionEvaluator INSTANCE = new ModelLevelExpressionEvaluator();

	protected ModelLevelLibraryFunctionFactory libraryFunctionFactory = ModelLevelLibraryFunctionFactory.INSTANCE;
	
	public ModelLevelLibraryFunctionFactory getLibraryFunctionFactory() {
		return libraryFunctionFactory;
	}
	
	public void setLibraryFunctionFactory(ModelLevelLibraryFunctionFactory libraryFunctionFactory) {
		this.libraryFunctionFactory = libraryFunctionFactory;
	}

	
	@Override
	public EList<Element> evaluate(Expression expression, Element target) {
		if (expression instanceof NullExpression) {
			return evaluateNull((NullExpression)expression, target);
		} else if (expression instanceof LiteralExpression) {
			return evaluateLiteral((LiteralExpression)expression, target);
		} else if (expression instanceof FeatureReferenceExpression) {
			return evaluateFeatureReference((FeatureReferenceExpression)expression, target);
		} else if (expression instanceof MetadataAccessExpression) {
			return evaluateMetadataAccess((MetadataAccessExpression)expression, target);
		} else if (expression instanceof InvocationExpression) {
			return evaluateInvocation((InvocationExpression)expression, target);
		} else if (expression instanceof ConstructorExpression) {
			return evaluateConstructor((ConstructorExpression)expression, target);
		} else {
			return evaluateExpression(expression, target);
		}		
	}
	
	public EList<Element> evaluateNull(NullExpression expression, Element target) {
		return EvaluationUtil.nullList();
	}
	
	public EList<Element> evaluateLiteral(LiteralExpression expression, Element target) {
		return EvaluationUtil.singletonList(expression);
	}
	
	public EList<Element> evaluateFeatureReference(FeatureReferenceExpression expression, Element target) {
		Feature referent = expression.getReferent();
//		return referent == null? null:
//			   evaluateFeature(referent, target instanceof Type? (Type)target: null);
		if (referent != null) {
			return referent instanceof Expression refexpr
					? evaluate(refexpr, target instanceof Type ? (Type) target : null)
					: evaluateFeature(referent, target instanceof Type ? (Type) target : null);
		}
		return null;
	}
	
	public EList<Element> evaluateMetadataAccess(MetadataAccessExpression expression, Element target) {
		Element referencedElement = expression.getReferencedElement();
		EList<Element> metadataFeatures = new BasicEList<>();
		metadataFeatures.addAll(ElementUtil.getAllMetadataFeaturesOf(referencedElement));
		MetadataFeature metaclassFeature = ElementUtil.getMetaclassFeatureFor(referencedElement);
		if (metaclassFeature != null) {
			metadataFeatures.add(metaclassFeature);
		}
		return metadataFeatures;
	}
	
	public EList<Element> evaluateInvocation(InvocationExpression expression, Element target) {
		LibraryFunction function = libraryFunctionFactory.getLibraryFunction(expression.getFunction());
		return function == null? EvaluationUtil.singletonList(expression): function.invoke(expression, target, this);
	}
	
	public EList<Element> evaluateConstructor(ConstructorExpression expression, Element target) {		
		Type instantiatedType = expression.instantiatedType();
		if (instantiatedType != null) {
			Feature newf = switch (instantiatedType.eClass().getName()) {
			    case "AttributeDefinition" -> SysMLFactory.eINSTANCE.createAttributeUsage();
			    case "PortDefinition"      -> SysMLFactory.eINSTANCE.createPortUsage();
			    case "PartDefinition"      -> SysMLFactory.eINSTANCE.createPartUsage();
			    case "ItemDefinition"      -> SysMLFactory.eINSTANCE.createItemUsage();
			    case "InterfaceDefinition" -> SysMLFactory.eINSTANCE.createInterfaceUsage();
			    case "FlowDefinition"      -> SysMLFactory.eINSTANCE.createFlowUsage();
			    default-> SysMLFactory.eINSTANCE.createFeature();				    
			};
			
			FeatureTyping newTyping = SysMLFactory.eINSTANCE.createFeatureTyping();
			newTyping.setType(instantiatedType);
			newTyping.setTypedFeature(newf);
			newf.getOwnedRelationship().add(newTyping);
			
			EList<Expression> arguments = new BasicEList<>();
			for (Expression argExpr : expression.getArgument()) {
				EList<Element> argVal = evaluate(argExpr,target);
				arguments.add(EvaluationUtil.expressionFor(argVal, expression));
			}
			
			Element[] argsArray = arguments.toArray(new Element[0]);
			EvaluationUtil.instantiateArguments(newf, EvaluationUtil.getFeature(instantiatedType), argsArray);
			
			if (expression.eResource() != null)	expression.eResource().getContents().add(newf);
			return EvaluationUtil.singletonList(newf);
		}
		return null;
	}
	
	public EList<Element> evaluateExpression(Expression expression, Element target, Element... arguments) {
		InvocationExpression invocation = EvaluationUtil.createInvocationOf(expression, arguments);
		
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
				if (expression.getType() instanceof Function typefunc) {
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
				return libraryFunction.invoke(invocation, target, this);
			}
		}
		
		Expression resultExpression = EvaluationUtil.getResultExpressionFor(expression);
		if (resultExpression == null) {
			return EvaluationUtil.singletonList(expression);
		} else {
			Feature targetFeature = EvaluationUtil.getTargetFeatureFor(target);
			EList<Element> results = evaluate(resultExpression, FeatureUtil.chainFeatures(targetFeature, invocation));
			return results == null? EvaluationUtil.singletonList(resultExpression): results;
		}
	}
	
	public EList<Element> evaluateFeature(Feature feature, Type type) {
		LibraryFeature libraryFeature = libraryFunctionFactory.getLibraryFeature(feature);
		if (libraryFeature != null) {
			return libraryFeature.getValue();
		} else if (type != null && TypeUtil.specializes(feature, ExpressionUtil.getSelfReferenceFeature(feature))) {
			// Evaluate "self" feature. (Note: Must be checked before test for feature chain because "self" has chaining features.)
			return EvaluationUtil.singletonList(EvaluationUtil.getTargetFeatureFor(type));
			
		} else if (!feature.getOwnedFeatureChaining().isEmpty()) {
			// Evaluate feature with a feature chain.
			return evaluateFeatureChain(feature.getChainingFeature(), type);
			
		} else {
			
			// If "type" has a feature chain, then this represents a nested context, to be searched in reverse 
			// from the last to the first chaining feature. (DISAGREE - this forged chain is not a nested context). 
			// The nested context is the Owning types chain of the last feature in the forged chain 
			// Except when there are invocations in the chain. The evaluation process stores their trace with feature chains
			// and cannot be addressed through owningType()
			List<Type> types = new BasicEList<>();
			if (type instanceof Feature typefeat) {
				List<? extends Type> typefeatChain = !typefeat.getOwnedFeatureChaining().isEmpty()? typefeat.getChainingFeature().reversed() : new BasicEList<>();
				for (Type t : typefeatChain) {					
					while (t != null) {
						if (!types.contains(t))types.add(t);
						if (t instanceof Feature featureTarget) {
							t = featureTarget.getOwningType();
						} else break;
					}
				}
				if (typefeatChain.isEmpty()) types.add(typefeat); 
			} else types.add(type); 
//			List<? extends Type> types = type instanceof Feature && !((Feature) type).getOwnedFeatureChaining().isEmpty()? // original version
//					((Feature) type).getChainingFeature().reversed(): 
//					Collections.singletonList(type);
			// Find the most specific type with a binding for the feature and evaluate it.
			int j =0;
			for (Type t : types) {
				if (t instanceof MetadataFeature && TypeUtil.specializes(feature, EvaluationUtil.getAnnotatedElementFeature((MetadataFeature)t))) {
					// Evaluate "Metaobject::annotatedElement" feature.
					return EvaluationUtil.results(((MetadataFeature)t).getAnnotatedElement());
				} else if (EvaluationUtil.isMetaclassFeature(t)) {
					if (!(feature instanceof Expression)) {
						// Evaluate the feature as a reflective metaclass attribute.
						Element element = ((AnnotatingElement)t).getAnnotatedElement().get(0);
						EStructuralFeature eFeature = element.eClass().getEStructuralFeature(feature.getDeclaredName());
						if (eFeature != null) {
							return EvaluationUtil.results(element.eGet(eFeature, true));
						}
					}
				} else {
					// Evaluate the feature as a regular binding.
					Feature typeFeature = EvaluationUtil.getTypeFeatureFor(feature, t);
					//Feature typeFeature = EvaluationUtil.getTypeFeatureForOld(feature, t); // Old kept for comparison
					if (typeFeature != null) {
						Expression valueExpression = FeatureUtil.getValueExpressionFor(typeFeature);
						if (valueExpression != null) {
							EList<Element> results = evaluate(valueExpression, type);
							if (results != null) {
								return results;
							}
						}
						return EvaluationUtil.singletonList(typeFeature);
					}
				}
			}
			Expression valueExpression = FeatureUtil.getValueExpressionFor(feature);			
			if (valueExpression != null) {
				EList<Element> results = evaluate(valueExpression, type);
				if (results != null) {
					return results;
				}
			}
			
			// If no value expression is found, or it is unevaluable, return the unevaluated feature.
			return EvaluationUtil.singletonList(feature);
		}
	}
	
	public EList<Element> evaluateFeatureChain(List<Feature> chainingFeatures, Type type) {
		EList<Element> values = evaluateFeature(chainingFeatures.get(0), type);
		if (chainingFeatures.size() == 1) {
			return values;
		} else {
			// Evaluate the chain of features other than the first, on each value from the
			// result of evaluating the first chaining feature.
			List<Feature> subchainingFeatures = chainingFeatures.subList(1, chainingFeatures.size());
			EList<Element> result = new BasicEList<>();
			for (Element value: values) {
				if (!(value instanceof Type)) {
					result.add(FeatureUtil.chainFeatures((Feature)value, FeatureUtil.chainFeatures(subchainingFeatures)));
				} else {
					Type target = value instanceof Feature? 
							FeatureUtil.chainFeatures(EvaluationUtil.getTargetFeatureFor(type), (Feature)value): 
							(Type)value;
					result.addAll(evaluateFeatureChain(subchainingFeatures, target));
				}
			}
			return result;
		}
	}
	
}
