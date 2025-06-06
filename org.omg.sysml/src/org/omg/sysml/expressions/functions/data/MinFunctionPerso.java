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

package org.omg.sysml.expressions.functions.data;

import org.eclipse.emf.common.util.EList;
import org.omg.sysml.expressions.ExpressionEvaluator;
import org.omg.sysml.expressions.functions.LibraryFunction;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.util.EvaluationUtil;


public class MinFunctionPerso implements LibraryFunction {

	@Override
	public String getPackageName() {
		return "DataFunctions";
	}

	@Override
	public String getFunctionName() {
		return "min";
	}
	
	@Override
	public EList<Element> invoke(InvocationExpression invocation, Element target, ExpressionEvaluator evaluator) {
		
		EList<Element> FirstOp = evaluator.evaluate(invocation.getArgument().get(0), target);
		EList<Element> SecondOp = evaluator.evaluate(invocation.getArgument().get(1), target);
		
		Object X = EvaluationUtil.valueOf(FirstOp.get(0));
		Object Y = EvaluationUtil.valueOf(SecondOp.get(0));
		Boolean XLowerThanY = false;

		if (X instanceof Integer && Y instanceof Integer) XLowerThanY = (Integer)X<(Integer)Y;
		else if (X instanceof Double && Y instanceof Integer) XLowerThanY = (Double)X<(Integer)Y;
		else if (X instanceof Integer && Y instanceof Double) XLowerThanY = (Integer)X<(Double)Y;
		else if (X instanceof Double && Y instanceof Double) XLowerThanY = (Double)X<(Double)Y;
		
		else if (X instanceof String && Y instanceof String) XLowerThanY =  ((String)X).compareTo((String)Y) < 0;
		
		else EvaluationUtil.singletonList(invocation);
		
		return XLowerThanY ? FirstOp : SecondOp;
	}
}

