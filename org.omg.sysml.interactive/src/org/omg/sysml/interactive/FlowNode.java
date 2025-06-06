package org.omg.sysml.interactive;

import java.util.*;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.resource.Resource;
import org.omg.sysml.execution.expressions.ExpressionEvaluator;
import org.omg.sysml.util.EvaluationUtil;
import org.omg.sysml.lang.sysml.CalculationUsage;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureValue;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.ItemUsage;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.impl.FeatureChainExpressionImpl;
import org.omg.sysml.lang.sysml.impl.FeatureReferenceExpressionImpl;
import org.omg.sysml.util.FeatureUtil;

public class FlowNode {

	private ContextChain calcContext;
	private Map<Feature, ContextChain> inputContextsMap;
	private ContextChain outputContext; 
	
	@Override
    public String toString() {
        String nodeString =  "FlowNode ";
        nodeString += "\n Calc = " + calcContext;
        nodeString += "\n Output = " + outputContext;
        return nodeString+"\n";
    }
	
	public FlowNode(ContextChain ctxt) {	
		inputContextsMap = new HashMap<>();	
		outputContext = ctxt;
	}

	public void setCalcContext(ContextChain ctxt) {
		this.calcContext = ctxt;
	}
	public ContextChain getCalcContext() {
		return calcContext;
	}
	
	
	public ContextChain findInputContext(Feature input, int index) {
		Feature calcInput = calcContext.getFeature().getInput().get(index);
		if (input instanceof FeatureChainExpressionImpl fcExpr) {
			if (!fcExpr.getArgument().isEmpty()) {
				Expression expr = fcExpr.getArgument().get(0);
				if (expr instanceof FeatureReferenceExpressionImpl frExpr) {
					Feature inputRef = frExpr.getReferent();
					Feature inputItem = fcExpr.getTargetFeature(); 
					ContextChain inputRefCtxt = calcContext.getOwningContext().findFeatureCtxt(inputRef);
					if (inputRefCtxt != null) {
						ContextChain inputItemCtxt = inputRefCtxt.findFeatureCtxt(inputItem);
						inputContextsMap.put(calcInput, inputItemCtxt);
						return inputItemCtxt;
					}
				}
			}
		}
		return null;
	}	
	public Map<Feature, ContextChain> getInputContexts() {
		return inputContextsMap;
	}
	public int nbExpectedInputs() { // cannot take inputContextsMap.keySet().size() because a same entry can fill several inputs
		Set<ContextChain> entries = new HashSet<>();
		for (Feature f : inputContextsMap.keySet()) {
			entries.add(inputContextsMap.get(f));
		}
		return entries.size();
	}
	
	public ContextChain getOutputContext() {
		return outputContext;
	}
	
	public ItemUsage process(Map<ContextChain, ItemUsage> inputs, List<Resource> inputResource) {
		
		if (calcContext == null) return (ItemUsage) outputContext.getFeature(); 

		CalculationUsage calc = (CalculationUsage) calcContext.getFeature();
		int nbInputs = inputs==null ? 0: inputs.size();
		Element[] argsArray = new Element[nbInputs];
		for (int j = 0; j < nbInputs; j++) {
			Feature calcInput = calc.getInput().get(j);
			argsArray[j] =  inputs.get(inputContextsMap.get(calcInput)); 
		}
		
		Feature owner = calcContext.getOwningContext().getFeature();
		Feature calcLocated = EvaluationUtil.getTypeFeatureFor(calc, owner); 
		InvocationExpression calcInvocation = EvaluationUtil.createInvocationOf(calcLocated, argsArray);
		EList<Element> invocRes = ExpressionEvaluator.INSTANCE.evaluate(calcInvocation, owner);

		ItemUsage outputItem = invocRes.size()==1 && invocRes.get(0) instanceof ItemUsage resItem ? resItem : null;	
		valueFeature(outputItem);

		if (calcLocated != calc) {
			owner.getOwnedRelationship().remove(calcLocated.getOwningMembership());
			EvaluationUtil.getFeature(owner).remove(calcLocated);
			EvaluationUtil.getFeature(owner).addFirst(calc);
		}
		return outputItem;		
	}

	public void valueFeature(Feature f) {
		Expression valueExpressionFor = FeatureUtil.getValueExpressionFor(f);
		if (valueExpressionFor!= null) {
			EList<Element> r = ExpressionEvaluator.INSTANCE.evaluate(valueExpressionFor, null);
			for (Element element : r) {
				if(element instanceof Feature eltfeat) valueFeature(eltfeat);
			}
			f.getOwnedRelationship().remove(FeatureUtil.getValuationFor(f));
			FeatureValue newFeatureValue = SysMLFactory.eINSTANCE.createFeatureValue();
			newFeatureValue.setValue(EvaluationUtil.expressionFor(r, f));
			f.getOwnedRelationship().add(newFeatureValue);
		}
		for (Feature sf : f.getOwnedFeature()) valueFeature(sf);
	}
}
