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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.omg.sysml.util.TypeUtil;

public class PythonFunction implements LibraryFunction { //Added at the end of the LibraryFunctionFactory

	@Override
	public String getPackageName() {
		// TODO
		return "TODO";
	}

	@Override
	public String getOperatorName() {
		// TODO
		return "python";
	}

	@Override
	public EList<Element> invoke(InvocationExpression invocation, Element target, ModelLevelExpressionEvaluator evaluator) {

		// using processbuilder, best solution ? 
		EList<Element> result = new BasicEList<>();

//		String scriptPath = (String) invocation.getArgument().get(0); // to complete with a "get..." to access the value of the argument. Will depend of the architecture of pythonFunc in SysMLV2
//		String pythonExecutable = "python3"; // Should be in the PATH, TODO
//		StringBuilder output = new StringBuilder(); //using strings readings as outputs, the best/only solution ?
//
//		ProcessBuilder processBuilder = new ProcessBuilder(pythonExecutable, scriptPath);
//		processBuilder.redirectErrorStream(true);
//
//		try {
//			Process process = processBuilder.start();
//		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//			String line;
//			while ((line = reader.readLine()) != null) {
//				output.append(line + "\n");	
//				// Determine what kind of output delivers the python scripts, first idea is to put them in a string that could be parsed back in the resource 
//			}
//		process.waitFor();
//		} catch (IOException | InterruptedException e) {
//			e.printStackTrace();
//	}
//		if (!output.isEmpty()) {
//			// TODO parsing the output into EList<Element> result.
//		}
		return result;
	}
}
