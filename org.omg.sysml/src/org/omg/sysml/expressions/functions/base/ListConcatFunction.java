/*******************************************************************************
 * SysML 2 Pilot Implementation
 * Copyright (c) 2021 Model Driven Solutions, Inc.
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
 * You should have received a copy of theGNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *  
 * @license LGPL-3.0-or-later <http://spdx.org/licenses/LGPL-3.0-or-later>
 *  
 *******************************************************************************/

package org.omg.sysml.expressions.functions.base;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.omg.sysml.expressions.ExpressionEvaluator;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.util.EvaluationUtil;

public class ListConcatFunction extends BaseFunction {

	@Override
	public String getFunctionName() {
		return "','";
	}

	@Override
	public EList<Element> invoke(InvocationExpression invocation, Element target, ExpressionEvaluator evaluator) {
		//if (invocation.getArgument().get(0) instanceof FeatureReferenceExpression featrefexpr) {
		//	Feature referent = featrefexpr.getReferent();
		//	Type type = referent.getType().isEmpty()?null : referent.getType().get(0);
		//	if (type.getOwner().getName().equals("Collections")) {
				// Is there a way to evaluate a in 
				// 			b : Collection = (2,3);
				//			a : Collection =  = (1,b);
				// not as (1,2,3) but (1,(2,3) ?
				// The problem is the output of this invoke must be EList<Element>, and Element cannot be an iterable
				// Changing the output type would have big consequences, since this collection can be used in another expression
				// Another issue is that (1,2,3,4) is parsed as (1,(2,(3,4))) so there are undetectable structures
				// (0,1,(2,3)) and (0,1,2,3) are identical in the emf resource 
		//	}
		//}		
		EList<Element> list = evaluator.evaluateArgument(invocation, 0, target);
		if (list != null) {
			EList<Element> result = new BasicEList<>(list);
			list = evaluator.evaluateArgument(invocation, 1, target);
			if (list != null) {
				result.addAll(list);
				return result;
			}
		}		
		return EvaluationUtil.singletonList(invocation);
	}
	
}
