package org.omg.sysml.interactive;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.omg.sysml.lang.sysml.CalculationUsage;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.ItemUsage;
import org.omg.sysml.lang.sysml.Package;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.Type;
import org.omg.sysml.util.FeatureUtil;

public class SysMLFlowExecutor {
	
	private static Map<Feature, Map<ContextChain, Set<ContextChain>>> flowMaps;
	
	private static Set<FlowNode> nodes;
	private static Set<FlowNode> inputNodes;
	private static Map<ContextChain, Set<FlowNode>> inputsMap;

	public Feature execute(SysMLLocus locus, Feature targetItem, Package pckg, ContextChain targetCtxt, List<Resource> inputResource) {
		nodes = new HashSet<>();
		inputNodes = new HashSet<>();		
		inputsMap = new HashMap<>();
		flowMaps = locus.getFlowMaps().get(pckg);
		
		for (Feature i : locus.getSourceMaps().get(pckg).keySet()) {
			Set<ContextChain> sourceSet = locus.getSourceMaps().get(pckg).get(i);
			for (ContextChain s : sourceSet) createNode(s);
		}
		
		Set<FlowNode> relevantNodes = listRelevantNodes(targetCtxt);

		// list of ready nodes containing initially inputNodes (modify it or new queue ?)
		// pick one, execute action on inputs or evaluate item as it is if no action 
		// get output, send item to next inputs on the flow (tricky, are they necessarily ends?) Do we need a map to reverse FlowNode.getInputContexts() ? 
		// if input added to a FlowNode complete its input size, add it to ready queue
		// when finding real ends (i.e. outputs that get to no new input) add it to results
		// instead better to add an output target, we only want to know the item values in this port. Allow also "probing", get info on a non-final output.
				
		Map<FlowNode, Map<ContextChain, ItemUsage>> inputBuffer = new HashMap<>();
        List<FlowNode> readyQueue = new BasicEList<>(inputNodes);

        while (!readyQueue.isEmpty()) {
        	FlowNode node = readyQueue.removeFirst();
	        if (relevantNodes.contains(node)) {
	            Map<ContextChain, ItemUsage> inputs = inputBuffer.get(node);
	            ItemUsage output = node.process(inputs, inputResource);

	            List<ContextChain> nextCtxts = new BasicEList<>();
	            nextCtxts.add(node.getOutputContext());
	            while (!nextCtxts.isEmpty()) {
	            	ContextChain nextContextChain = nextCtxts.removeFirst();
	            	
	            	if (nextContextChain == targetCtxt) {
	            		if (node.getCalcContext()==null) { // only to get an easily displayed output
	            			Feature outputCopy = EcoreUtil.copy(output);
	            			Feature realOwner = node.getOutputContext().getOwningContext().getFeature();
	            			
	            			FeatureMembership membership = SysMLFactory.eINSTANCE.createFeatureMembership();
	            			membership.setOwnedMemberFeature(outputCopy);
	            			realOwner.getOwnedRelationship().add(membership);
	            			
	            			node.valueFeature(outputCopy);
	            			realOwner.getOwnedRelationship().remove(membership);
	            			return outputCopy;
	            		}
	            		return output;
	            	}
	            	
	            	for (FlowNode nextNode : inputsMap.getOrDefault(nextContextChain, new HashSet<>())) {
	                   	inputBuffer.computeIfAbsent(nextNode, k -> new HashMap<>()).put(nextContextChain, output);
	                   	if (inputBuffer.get(nextNode).size()==nextNode.nbExpectedInputs()) readyQueue.add(nextNode);
	                }
	            	for (Feature f : flowMaps.keySet()) {
		                nextCtxts.addAll(flowMaps.get(f).getOrDefault(nextContextChain, new HashSet<>()));
					} 
	            }
	        }
        }
        return null;
    }



	private Set<FlowNode> listRelevantNodes(ContextChain target) {
	
		Map<ContextChain, FlowNode> outputsMap = new HashMap<>();
		Map<ContextChain, Set<ContextChain>> reverseFlowMap = reverseFlowMap(outputsMap);
		
		Set<FlowNode> relevantNodes = new HashSet<>();	
		relevantNodes.add(outputsMap.get(target)); 
		List<ContextChain> prevCtxts = new BasicEList<>();
		Set<ContextChain> c = reverseFlowMap.get(target);
		if (c!=null) prevCtxts.addAll(c);
		
        while (!prevCtxts.isEmpty()) {
        	ContextChain prevContextChain = prevCtxts.removeFirst();
        	FlowNode involvedNode = outputsMap.get(prevContextChain); 
			if (involvedNode != null) relevantNodes.add(involvedNode);
			prevCtxts.addAll(reverseFlowMap.getOrDefault(prevContextChain, new HashSet<>()));
		}
		return relevantNodes;
	}

	
	
	private Map<ContextChain, Set<ContextChain>> reverseFlowMap(Map<ContextChain, FlowNode> outputsMap) {
		Map<ContextChain, Set<ContextChain>> reverseFlowMap = new HashMap<>();
		for (FlowNode flowNode : nodes) { // add the reverseFlow between an outputCtxt and and the inputCtxt of the node
			ContextChain nodeOutputCtxt = flowNode.getOutputContext();
			outputsMap.put(nodeOutputCtxt, flowNode);
			for (ContextChain nodeInputCtxt : flowNode.getInputContexts().values()) {
				reverseFlowMap.computeIfAbsent(nodeOutputCtxt, k -> new HashSet<>()).add(nodeInputCtxt);
			}
		}
		for (Map<ContextChain, Set<ContextChain>> flowMap : flowMaps.values()) {
			for (ContextChain source : flowMap.keySet()) { // add the reverseFlow based on interface established flows
				for (ContextChain target : flowMap.get(source)) {
					reverseFlowMap.computeIfAbsent(target, k -> new HashSet<>()).add(source);
				}
			}
		}
		return reverseFlowMap;
	}



	private void createNode(ContextChain ctxt) {
		FlowNode node = new FlowNode(ctxt);
		nodes.add(node);
		List<Feature> calcAndInputs = findAssociatedCalcAndInput(ctxt);
		if (!calcAndInputs.isEmpty()) {		
			CalculationUsage calc = (CalculationUsage) calcAndInputs.removeFirst();
			for (ContextChain c : ctxt.getOwningContext().getOwningContext().getOwnedContexts()) { 
				// Model structure with 
				// calc {...} ;
				// port outputPort {
				//		out item itemUsage = calc (...);
				// }
				// and ctxt is [...->outputPort->itemUsage]
				
				if (c.getFeature() == calc || FeatureUtil.getAllRedefinedFeaturesOf(c.getFeature()).contains(calc)) {
					node.setCalcContext(c);
					break;
				} //TODO add what if not found ?
			}

			if (calcAndInputs.isEmpty()) inputNodes.add(node);

			for (int i = 0; i < calcAndInputs.size(); i++) {
				ContextChain inputContext = node.findInputContext(calcAndInputs.get(i), i);
				if (inputContext!=null) inputsMap.computeIfAbsent(inputContext,  k -> new HashSet<>()).add(node); // TODO add what if not found ?
			}
		} else inputNodes.add(node);

	}

	

	private List<Feature> findAssociatedCalcAndInput(ContextChain ctxt) {
		List<Feature> res= new BasicEList<>();
		Expression valExpr = FeatureUtil.getValueExpressionFor(ctxt.getFeature());
		if (valExpr != null && valExpr instanceof InvocationExpression invocExpr) {
			Type instantiatedType = invocExpr.instantiatedType();
			if (instantiatedType != null && instantiatedType instanceof CalculationUsage calc ) {
				EList<Expression> inputs = invocExpr.getArgument();
				res.add(calc);
				res.addAll(inputs);
			}
			// Feature target = fcExpr.getTargetFeature(); //TODO first we will assume that actions have only one output, the desired item
		}
		return res;
	}
}
