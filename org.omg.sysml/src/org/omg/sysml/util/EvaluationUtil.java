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

package org.omg.sysml.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.omg.sysml.expressions.ExpressionEvaluator;
import org.omg.sysml.expressions.ModelLevelExpressionEvaluator;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureDirectionKind;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.FeatureReferenceExpression;
import org.omg.sysml.lang.sysml.FeatureTyping;
import org.omg.sysml.lang.sysml.FeatureValue;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.LiteralBoolean;
import org.omg.sysml.lang.sysml.LiteralExpression;
import org.omg.sysml.lang.sysml.LiteralInfinity;
import org.omg.sysml.lang.sysml.LiteralInteger;
import org.omg.sysml.lang.sysml.LiteralRational;
import org.omg.sysml.lang.sysml.LiteralString;
import org.omg.sysml.lang.sysml.Membership;
import org.omg.sysml.lang.sysml.MetadataFeature;
import org.omg.sysml.lang.sysml.Redefinition;
import org.omg.sysml.lang.sysml.Specialization;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.SysMLPackage;
import org.omg.sysml.lang.sysml.Type;
import org.omg.sysml.lang.sysml.util.SysMLLibraryUtil;

public class EvaluationUtil {

	public static Feature getAnnotatedElementFeature(MetadataFeature metadata) {
		EList<Element> annotatedElements = metadata.getAnnotatedElement();
		return (Feature)SysMLLibraryUtil.getLibraryType(
				annotatedElements.isEmpty()? metadata: annotatedElements.get(0), 
				ImplicitGeneralizationMap.getDefaultSupertypeFor(metadata.getClass(), "annotatedElement"));
	}
	
	public static Type getPrimitiveType(Element context, EClass eClass) {
		return 
			eClass == SysMLPackage.eINSTANCE.getLiteralBoolean()? 
					(Type)SysMLLibraryUtil.getLibraryElement(context, "ScalarValues::Boolean"):
			eClass == SysMLPackage.eINSTANCE.getLiteralString()? 
					(Type)SysMLLibraryUtil.getLibraryElement(context, "ScalarValues::String"):
			eClass == SysMLPackage.eINSTANCE.getLiteralInteger()? 
					(Type)SysMLLibraryUtil.getLibraryElement(context, "ScalarValues::Integer"):
			eClass == SysMLPackage.eINSTANCE.getLiteralRational()? 
					(Type)SysMLLibraryUtil.getLibraryElement(context, "ScalarValues::Rational"):
			eClass == SysMLPackage.eINSTANCE.getLiteralInfinity()? 
					(Type)SysMLLibraryUtil.getLibraryElement(context, "ScalarValues::Positive"):
			null;
	}

	public static int numberOfArgs(InvocationExpression invocation) {
		return invocation.getArgument().size();
	}

	public static EList<Element> evaluate(Expression expression, Element target) {
		return ModelLevelExpressionEvaluator.INSTANCE.evaluate(expression, target);
	}

	public static EList<Element> nullList() {
		return new BasicEList<>();
	}

	public static EList<Element> singletonList(Element element) {
		if (element == null) {
			return null;
		} else {
			EList<Element> result = new BasicEList<>();
			result.add(element);
			return result;
		}
	}
	
	public static Expression expressionFor(EList<Element> results, Element context) {
		if (!results.stream().allMatch(
				elm->elm instanceof Feature && 
				(!(elm instanceof Expression) || elm instanceof LiteralExpression || elm.eClass() == SysMLPackage.eINSTANCE.getExpression()))) {
			return null;
		} else if (results.isEmpty()) {
			return SysMLFactory.eINSTANCE.createNullExpression();
		} else {
			Expression expression = expressionFor(results.get(0));
			if (results.size() > 1) {
				Type listOp = SysMLLibraryUtil.getLibraryType(context, ExpressionUtil.getOperatorQualifiedNames(","));
				for (int i = 1; i < results.size(); i++) {
					InvocationExpression listExpr = SysMLFactory.eINSTANCE.createInvocationExpression();
					TypeUtil.addOwnedParameterTo(listExpr, expression);
					TypeUtil.addOwnedParameterTo(listExpr, expressionFor(results.get(i)));					
					NamespaceUtil.addMemberTo(listExpr, listOp);
					
					FeatureTyping typing = SysMLFactory.eINSTANCE.createFeatureTyping();
					typing.setType(listOp);
					typing.setTypedFeature(listExpr);
					listExpr.getOwnedRelationship().add(typing);
					
					expression = listExpr;
				}
			}
			ElementUtil.transformAll(expression, false);
			return expression;
		}
	}
	
	public static Expression expressionFor(Element result) {
		if (result instanceof LiteralExpression) {
			Object value = valueOf(result);
			return value == null? literalInfinity(): (Expression)elementFor(value);
		} else if (result instanceof Feature) {
			FeatureReferenceExpression featureRef = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
			NamespaceUtil.addMemberTo(featureRef, result);
			return featureRef;
		} else {
			return null;
		}
			   
	}

	public static EList<Element> results(Object object) {
		EList<Element> results = new BasicEList<>();
		if (object instanceof List) {
			((List<?>)object).stream().
				map(EvaluationUtil::elementFor).
				filter(e->e != null).
				forEachOrdered(results::add);
		} else if (object != null) {
			Element element = elementFor(object);
			if (object != null) {
				results.add(element);
			}
		}
		return results;
	}
	
	public static Element elementFor(Object object) {
		return object instanceof Boolean? literalBoolean((Boolean)object):
			   object instanceof String? literalString((String)object):
			   object instanceof Integer? literalInteger((Integer)object):
			   object instanceof Double? literalRational((Double)object):
			   object instanceof Element? ElementUtil.getMetaclassFeatureFor((Element)object):
			   null;
	}
	
	public static LiteralBoolean literalBoolean(boolean value) {
		LiteralBoolean literal = SysMLFactory.eINSTANCE.createLiteralBoolean();
		literal.setValue(value);
		return literal;
	}

	public static LiteralString literalString(String value) {
		LiteralString literal = SysMLFactory.eINSTANCE.createLiteralString();
		literal.setValue(value);
		return literal;
	}

	public static LiteralInteger literalInteger(int value) {
		LiteralInteger literal = SysMLFactory.eINSTANCE.createLiteralInteger();
		literal.setValue(value);
		return literal;
	}

	public static LiteralRational literalRational(double value) {
		LiteralRational literal = SysMLFactory.eINSTANCE.createLiteralRational();
		literal.setValue(value);
		return literal;
	}
	
	public static LiteralInfinity literalInfinity() {
		return SysMLFactory.eINSTANCE.createLiteralInfinity();
	}
	
	public static EList<Element> booleanResult(boolean value) {
		return singletonList(literalBoolean(value));
	}

	public static EList<Element> stringResult(String value) {
		return singletonList(literalString(value));
	}

	public static EList<Element> integerResult(int value) {
		return singletonList(literalInteger(value));
	}

	public static EList<Element> realResult(double value) {
		return singletonList(literalRational(value));
	}
	
	public static Object valueOf(Element element) {
		return element instanceof LiteralBoolean? Boolean.valueOf(((LiteralBoolean)element).isValue()):
			   element instanceof LiteralString? ((LiteralString)element).getValue():
			   element instanceof LiteralInteger? Integer.valueOf(((LiteralInteger)element).getValue()):
			   element instanceof LiteralRational? Double.valueOf(((LiteralRational)element).getValue()):
			   element instanceof LiteralInfinity? null:
			   element;
	}

	public static boolean equal(Element x, Element y) {
		Object x_value = valueOf(x);
		Object y_value = valueOf(y);
		return x_value == null? y_value == null:
			   x_value.equals(y_value);
	}

	public static boolean equal(List<Element> x, List<Element> y) {
		if (x.size() != y.size()) {
			return false;
		} else {
			for (int i = 0; i < x.size(); i++) {
				if (!equal(x.get(i), y.get(i))) {
					return false;
				}
			}
			return true;
		}
	}
	
	public static EList<Element> getElementsOf(Feature collection, ExpressionEvaluator evaluator) {
		List<Feature> elementsChain = new ArrayList<>();
		elementsChain.add(collection);
		elementsChain.add(ExpressionUtil.getCollectionElementsFeature(collection));
		return evaluator.evaluateFeatureChain(elementsChain, collection);		
	}

	public static Feature getTargetFeatureFor(Element target) {
		if (target instanceof Feature) {
			return (Feature)target;
		} else {
			Feature targetFeature = SysMLFactory.eINSTANCE.createFeature();
			if (target instanceof Type) {
				FeatureTyping featureTyping = SysMLFactory.eINSTANCE.createFeatureTyping();
				featureTyping.setType((Type)target);
				targetFeature.getOwnedRelationship().add(featureTyping);
			}
			return targetFeature;
		}
	}
	
	// old version, kept for comparison purposes	
	public static Feature getTypeFeatureForOld(Feature feature, Type type) {
		return type == null? null :
			type.getFeature().stream().
				filter(f->FeatureUtil.getAllRedefinedFeaturesOf(f).contains(feature)).
				findFirst().orElse(null);
	}
	
	public static Map<Type,EList<Feature>> typeFeatureCache = new HashMap<Type,EList<Feature>>();
	
	public static void initCaches() {
		 typeFeatureCache.clear();
	}
	
	public static EList<Feature> getFeature(Type type) {
		return typeFeatureCache.computeIfAbsent(type, k -> type.getFeature());
	}
	public static Feature getTypeFeatureFor(Feature feature, Type type) {
		if (type == null) return null;

		EList<Feature> features = getFeature(type);

		for (Feature f : features) {
			Set<Feature> allRedefinedFeaturesOf = FeatureUtil.getAllRedefinedFeaturesOf(f);
			if (allRedefinedFeaturesOf.contains(feature)) {
				if (f.getOwner() == type) {
					return f;
				}
				else {
					Feature referenceUsage = createRedef(type, f);
					features.remove(f); // added
					features.addFirst(referenceUsage); // added
					return referenceUsage;
				}
			}		
		}
		return null;
	}

	public static Feature createRedef(Type type, Feature f) {
		Feature newf = (Feature) SysMLFactory.eINSTANCE.create(f.eClass());
		
		String fname = f.getDeclaredName();
		String tname = type.getDeclaredName();
		if (fname!=null) {
			if (tname != null) newf.setDeclaredName(tname+"_"+fname);
			else newf.setDeclaredName(fname+"_ref");
		}
		newf.getInheritedMembership().addAll(f.getFeatureMembership());
		
		Expression fValueExpression = FeatureUtil.getValueExpressionFor(f);
		if (fValueExpression != null) {
			FeatureValue featureValue = SysMLFactory.eINSTANCE.createFeatureValue();
			FeatureReferenceExpression featRefExpr = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();

			Membership referentmembership = SysMLFactory.eINSTANCE.createMembership();
			referentmembership.setMemberElement(fValueExpression);
			featRefExpr.getOwnedRelationship().add(referentmembership);

			featureValue.getOwnedRelatedElement().add(featRefExpr);
			newf.getOwnedRelationship().add(featureValue);
		}

		Redefinition redefinition = SysMLFactory.eINSTANCE.createRedefinition();
		redefinition.setRedefinedFeature(f);
		newf.getOwnedRelationship().add(redefinition);

		FeatureMembership membership = SysMLFactory.eINSTANCE.createFeatureMembership();
		membership.setOwnedMemberFeature(newf);
		membership.setOwningType(type);
		
		type.getOwnedRelationship().add(membership);
		typeFeatureCache.put(newf, getFeature(f));
		
		//FeatureUtil.getAllRedefinedFeaturesOf(newf).addAll(FeatureUtil.getAllRedefinedFeaturesOf(f)); //added
		// immutable : newf.getOwnedSubsetting().add(redefinition); //added
		// immutable : FeatureUtil.getSubsettedFeaturesOf(newf).add(f);
		newf.setIsAbstract(f.isAbstract());
		newf.setIsEnd(f.isEnd());
		//NamespaceUtil.addMemberTo(type, f);

		return newf;
	} 
	
	
	public static Expression getResultExpressionFor(Type type) {
		Expression resultExpression = null;
		if (type != null) {
			resultExpression = ExpressionUtil.getResultExpressionOf(type);
			if (resultExpression == null) {
				Feature resultParameter = TypeUtil.getResultParameterOf(type);
				if (resultParameter != null) {
					resultExpression = FeatureUtil.getValueExpressionFor(resultParameter);
				}
			}
		}
		return resultExpression;
	}
	
//	private void valueFeature(Feature f, Element target) {
//		Expression valueExpressionFor = FeatureUtil.getValueExpressionFor(f);
//		if (valueExpressionFor!= null) {
//			EList<Element> r = ExpressionEvaluator.INSTANCE.evaluate(valueExpressionFor, target);
//			for (Element element : r) {
//				if(element instanceof Feature eltfeat) valueFeature(eltfeat, target);
//			}
//			f.getOwnedRelationship().remove(FeatureUtil.getValuationFor(f));
//			FeatureValue newFeatureValue = SysMLFactory.eINSTANCE.createFeatureValue();
//			newFeatureValue.setValue(EvaluationUtil.expressionFor(r, target));
//			f.getOwnedRelationship().add(newFeatureValue);
//		}
//		for (Feature sf : f.getOwnedFeature()) valueFeature(sf, target);
//	}

	public static boolean isMetaclassFeature(Element element) {
		return getMetaclassReferenceOf(element) != null;
	}
	
	public static Element getMetaclassReferenceOf(Element element) {
		return !(element instanceof MetadataFeature)? null:
			((MetadataFeature)element).getAnnotatedElement().stream().
				filter(elm->ElementUtil.getMetaclassFeatureFor(elm) == element).
				findFirst().orElse(null);
	}

	public static Type getTypeArgument(InvocationExpression invocation) {
		EList<Feature> ownedFeatures = invocation.getOwnedFeature();
		if (ownedFeatures.size() >= 2) {
			EList<Type> types = ownedFeatures.get(1).getType();
			if (!types.isEmpty()) {
				return types.get(0);
			}
		}
		return null;
	}

	public static List<Type> getType(Element context, Element element) {
		return element instanceof LiteralExpression? Collections.singletonList(getPrimitiveType(context, element.eClass())):
			   element instanceof Feature? ((Feature)element).getType():
			   Collections.emptyList();
	}

	public static boolean isType(Element context, Element element, Type type) {
		return getType(context, element).stream().
				anyMatch(elementType->TypeUtil.specializes(elementType, type));
	}

	public static boolean hasType(Element context, Element element, Type type) {
		return getType(context, element).contains(type);
	}

	public static boolean isMetatype(Element element, Type targetType) {
		return TypeUtil.specializes(ElementUtil.getMetaclassOf(element), targetType);
	}
	
	public static void instantiateArguments(Feature target, List<Feature> parameters, Element[] arguments) {
		for (int i = 0; i < arguments.length && i < parameters.size(); i++) {
			Element argument = arguments[i];
			Feature parameter = parameters.get(i);
			if (argument instanceof Feature) {
				Feature actual = SysMLFactory.eINSTANCE.createFeature();
				actual.setDirection(FeatureDirectionKind.IN);
				TypeUtil.addOwnedFeatureTo(target, actual);
				
				Expression valueExpr;
				//if (argument instanceof Expression) {
				//	valueExpr = (Expression)argument;
				//} else {
				valueExpr = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
				NamespaceUtil.addMemberTo(valueExpr, argument);
				//}
				
				FeatureValue featureValue = SysMLFactory.eINSTANCE.createFeatureValue();
				featureValue.setValue(valueExpr);
				actual.getOwnedRelationship().add(featureValue);
				
				Redefinition redefinition = SysMLFactory.eINSTANCE.createRedefinition();
				redefinition.setRedefinedFeature(parameter);
				redefinition.setRedefiningFeature(actual);
				actual.getOwnedRelationship().add(redefinition);
			}
		}
	}
	
	public static InvocationExpression createInvocationOf(Type type, Element... arguments) {
		InvocationExpression invocation = SysMLFactory.eINSTANCE.createInvocationExpression();
		NamespaceUtil.addMemberTo(invocation, type);
		
		Specialization specialization = SysMLFactory.eINSTANCE.createSpecialization();
		specialization.setGeneral(type);
		specialization.setSpecific(invocation);
		invocation.getOwnedRelationship().add(specialization);

		List<Feature> parameters = new BasicEList<>();
		for (Feature feature : TypeUtil.getAllParametersOf(type)) {
			if (feature.getDirection().equals(FeatureDirectionKind.IN)) parameters.add(feature);
		}
		
		//EList<Feature> parameters = type.getInput();
		instantiateArguments(invocation, parameters, arguments);
		
//		Expression resultExpression = EvaluationUtil.getResultExpressionFor(invocation);
//		if (resultExpression != null) {
//			FeatureReferenceExpression featRefExpr = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
//			NamespaceUtil.addMemberTo(invocation, featRefExpr);
//			
//			Membership referentmembership = SysMLFactory.eINSTANCE.createMembership();
//			referentmembership.setMemberElement(resultExpression);
//			featRefExpr.getOwnedRelationship().add(referentmembership);
//			
//			ResultExpressionMembership resultMembership = SysMLFactory.eINSTANCE.createResultExpressionMembership();
//			resultMembership.setMemberElement(featRefExpr);
//			invocation.getOwnedRelationship().addFirst(resultMembership);	
//		}
		
		return invocation;
	}

}
