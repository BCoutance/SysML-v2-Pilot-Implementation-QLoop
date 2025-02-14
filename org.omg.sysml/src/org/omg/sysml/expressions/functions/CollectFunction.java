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

package org.omg.sysml.expressions.functions;

import java.util.List;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.omg.sysml.expressions.ModelLevelExpressionEvaluator;
import org.omg.sysml.expressions.util.EvaluationUtil;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureChainExpression;
import org.omg.sysml.lang.sysml.FeatureReferenceExpression;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.Membership;
import org.omg.sysml.lang.sysml.Namespace;
import org.omg.sysml.lang.sysml.PartDefinition;
import org.omg.sysml.lang.sysml.PartUsage;
import org.omg.sysml.lang.sysml.ReferenceUsage;
import org.omg.sysml.lang.sysml.ResultExpressionMembership;
import org.omg.sysml.lang.sysml.Type;
import org.omg.sysml.lang.sysml.impl.ElementImpl;
import org.omg.sysml.util.FeatureUtil;
import org.omg.sysml.util.TypeUtil;

public class CollectFunction implements LibraryFunction {

	@Override
	public String getPackageName() {
		return "ControlFunctions";
	}

	@Override
	public String getOperatorName() {
		return "collect";
	}

	@Override
	public EList<Element> invoke(InvocationExpression invocation, Element target, ModelLevelExpressionEvaluator evaluator) {

		// String tokenText = NodeModelUtils.getTokenText(NodeModelUtils.getNode(invocation.getArgument().get(1)));

		EList<Element> list = evaluator.evaluateArgument(invocation, 0, target);
		EList<Element> result = new BasicEList<>();

		Feature mapper = ((FeatureReferenceExpression)invocation.getArgument().get(1)) .getReferent();

		Membership inArg = mapper.getOwnedMembership().get(0);
		Type type2match = ((ReferenceUsage)inArg.getTarget().get(0)) .getType().get(0);						
		InvocationExpression expression = (InvocationExpression) mapper.getOwnedMembership().get(1)
				.getOwnedRelatedElement().get(0);

		if (list != null) {				
			for (Element element : list) {					
				if (element instanceof PartUsage elementPartUsage) {
					if (type2match == elementPartUsage.getPartDefinition().get(0)) {
						
						// avec getType() on peut simplifier
						
						// Case only if mapper is of type FeatureChainExpression
						// FeatureReferenceExpression refexpr = (FeatureReferenceExpression) expression.getArgument().get(0);
						Feature targetFeature = ((FeatureChainExpression) expression).getTargetFeature();
						
						// it creates a path cycle : do not use the bellow method  
						// String expr2eval = "calc{private import " + target.getDeclaredName() + "::*;\n" 
						//		+ elementPartUsage.getDeclaredName() +"."+ targetFeature.getDeclaredName() + "}";

						//SysMLInteractiveResult localResult = SysMLInteractive.process(expr2eval, false); //qu'est ce que le isAddResource ? (ici set à false)
						//if (!localResult.hasErrors()) {
						//	Type localCalc = (Type) ((Namespace) localResult.getRootElement()).getOwnedMember().get(0);
						//	Expression localExpr = (Expression) TypeUtil.getFeatureByMembershipIn(localCalc, ResultExpressionMembership.class);
						//	elementResult = ExpressionEvaluator.INSTANCE.evaluate(localExpr, target);
						//}
						
						Feature elementResult = elementPartUsage.getFeature().stream().
							filter(f->f.getName() == targetFeature.getName()).	
							findFirst().orElse(null);
						// search by the name, a search by definition root would be more precise. Actually it is the most efficient and makes sense in terms of collect semantics
						// we have a part usage
						// turn it into an expression to evaluate
						
						Type type = targetFeature instanceof Feature? 
								FeatureUtil.chainFeatures(EvaluationUtil.getTargetFeatureFor(target), (Feature)targetFeature): 
								(Type)targetFeature;						
						EList<Element> values = evaluator.evaluateFeature(elementResult, type);
						
						// Feature referent = refexpr.getReferent();
						// referent, named p, expressed by "in p : N;" is a ReferenceUsageImpl, i.e. a reference to an object 
						// exterior of the system. Its definition is N
						// We want it to behave like a partUsage in order to reset "p" as "element".

						// referent.setFeatureTarget((Feature) element);
						// refexpr.setReferent((Feature) element);							
						// evaluator.evaluateFeatureChain(expression, element);

						// EList<Element> elementResult = evaluator.expressionValue(expression, 0, element);
						// renvoie p, sans y avoir appliqué element
						// il faudrait p-e créer une chaining feature à base de expression qui est une FeatureChainExpression
						// pour pouvoir ensuite l'évaluer avec evaluateFeatureChain comme lorsqu'on fait %eval --target=ab3 a1.valeur

						if (values.get(0) != null) {
							result.add(values.get(0));
						}
					}
				}

			}
			return result;
		}
		return EvaluationUtil.singletonList(invocation);
	}

}
