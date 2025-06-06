package org.omg.sysml.interactive;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.omg.sysml.util.EvaluationUtil;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FlowUsage;
import org.omg.sysml.lang.sysml.InterfaceDefinition;
import org.omg.sysml.lang.sysml.InterfaceUsage;
import org.omg.sysml.lang.sysml.ItemUsage;
import org.omg.sysml.lang.sysml.Namespace;
import org.omg.sysml.util.FeatureUtil;
import org.omg.sysml.lang.sysml.Package;

public class SysMLLocus {
	
	private Resource resource;
	private List<Resource> inputResources;
	
	private Map<Package,Map<Integer,Set<ContextChain>>> contextMaps;
	private Map<Package,Map<ContextChain,Set<ContextChain>>> connectionMaps;
	private Map<Package,Map<Feature,Map<ContextChain,Set<ContextChain>>>> flowMaps;
	private Map<Package,Map<Feature,Set<ContextChain>>> sourceMaps;
	
	public SysMLLocus(Resource myresource, List<Resource> myinputResources) {
		resource = myresource;
		inputResources = myinputResources;
		
		contextMaps = new HashMap<>();
		connectionMaps = new HashMap<>();
		flowMaps = new HashMap<>();
		sourceMaps = new HashMap<>();

		EList<EObject> contents = resource.getContents();
		
		if (!contents.isEmpty() && contents.get(0) instanceof Namespace namespace) {
			for (Element element : namespace.getOwnedElement()) {
				if (element instanceof Package pckg) {

					ContextChain.resetIdCounter();
					Map<Integer,Set<ContextChain>> contextMap =  new TreeMap<>();
					contextMaps.put(pckg, contextMap);

					List<ContextChain> itfList = new BasicEList<>();
					
					Map<ContextChain,Set<ContextChain>> connectionMap = new HashMap<>();
					connectionMaps.put(pckg, connectionMap);
					
					Map<Feature,Map<ContextChain,Set<ContextChain>>> flowMap = new HashMap<>();
					flowMaps.put(pckg, flowMap);
					
					Map<Feature,Set<ContextChain>> sourceMap = new HashMap<>();
					sourceMaps.put(pckg, sourceMap);
					Map<Feature,Set<ContextChain>> endMap = new HashMap<>();
					
					for (Element element0 : pckg.getOwnedElement()) {
						if (element0 instanceof Feature feature) {
							ContextChain featureCtxt = new ContextChain(feature);
							completeCtxtList(feature, featureCtxt, contextMap, itfList);
						}
					}
					
					for (int i : contextMap.keySet()) {
						for (ContextChain ctxt : contextMap.get(i)) {
							for (Feature sf : FeatureUtil.getSubsettedFeaturesOf(ctxt.getFeature())) {
								if (inputResources.contains(sf.eResource())) {
									ContextChain sub = findSub(ctxt, ctxt.getOwningContext(), sf);
									if (sub != null) sub.addDependentContext(ctxt);
								}
							}
						}
					}
					for (int i : contextMap.keySet()) {
						for (ContextChain ctxt : contextMap.get(i)) propagateCtxtDep(ctxt);
					}
					for (ContextChain itf : itfList) resolveItf(itf, connectionMap, flowMap, sourceMap, endMap);
				}
			}
		}
	}

	private void completeCtxtList(Feature feature, ContextChain currentCtxt, Map<Integer,Set<ContextChain>> contextMap, List<ContextChain> itfList) {
		contextMap.computeIfAbsent(currentCtxt.size(), k -> new HashSet<>()).add(currentCtxt);
		if (feature instanceof InterfaceUsage) {
			itfList.add(currentCtxt);
		}
		for (Feature subFeature : EvaluationUtil.getFeature(feature)) {
			if (inputResources.contains(subFeature.eResource()) && !currentCtxt.getFeatureList().contains(subFeature)) {
				ContextChain newCurrentCtxt = new ContextChain(currentCtxt, subFeature);
				if(subFeature.isAbstract()) newCurrentCtxt.setAbstract(true);
				completeCtxtList(subFeature, newCurrentCtxt, contextMap, itfList);
			} 
		}
	}

	private static ContextChain findSub(ContextChain ctxt, ContextChain widerCtxt, Feature sf) {
		for (ContextChain sibling : widerCtxt.getOwnedContexts()) {
			if (sibling != ctxt && sibling.getFeature() == sf) return sibling;
		}
		for (ContextChain dependingCtxt : widerCtxt.getDependingContexts()) {
			return findSub(ctxt, dependingCtxt, sf);
		}
		return null;
	}
	
	private static void propagateCtxtDep(ContextChain ctxt) {
		for (ContextChain greatBrother : ctxt.getDependingContexts()) { // should it be before or after "son loop", or no change ?
			addIndirectDep(greatBrother, ctxt);
		} 
		for (ContextChain son : ctxt.getOwnedContexts()) {
			for (ContextChain greatBrother : ctxt.getDependingContexts()) {
				for (ContextChain nephew : greatBrother.getOwnedContexts()) {
					if (son.getFeature() == nephew.getFeature()) {
						nephew.addDependentContext(son);
					}	
				}
			}
		}
	}
	
	private static void addIndirectDep (ContextChain greatBrother, ContextChain ctxt) {
		for (ContextChain d : greatBrother.getDependingContexts()) {
			d.addDependentContext(ctxt);
			addIndirectDep(d, ctxt);
		}
	}

	private void resolveItf(
			ContextChain itf, 
			Map<ContextChain,Set<ContextChain>> connectionMap, 
			Map<Feature,Map<ContextChain,Set<ContextChain>>> flowMap,
			Map<Feature,Set<ContextChain>> sourceMap,
			Map<Feature,Set<ContextChain>> endMap) 
	{
		Feature feat = itf.getFeature();
		if (feat instanceof InterfaceUsage itfFeat) {
			EList<Element> source = itfFeat.getSource();    
			EList<Element> target = itfFeat.getTarget();
			if (!source.isEmpty() && !target.isEmpty()) {
				if (source.get(0) instanceof Feature port1 && target.get(0) instanceof Feature port2) {
					
					Set<ContextChain> scope = itf.getOwningContext().getRecursivelyOwnedContexts();
					List<FlowUsage> flows = new BasicEList<>();
					for (ContextChain c : itf.getOwnedContexts()) {
						if (c.getFeature() instanceof FlowUsage flow) flows.add(flow); 
					}
					
					ContextChain end1 = resolveEnd(port1, itf.getOwningContext());
					Set<ContextChain> impactedEnd1 = new HashSet<>();
					if (end1!=null) {
						if (!end1.isAbstract()) impactedEnd1.add(end1);
						for (ContextChain c : end1.getDependentContexts()) {
							if (!c.isAbstract() && scope.contains(c)) impactedEnd1.add(c);
						}
					}
					
					ContextChain end2 = resolveEnd(port2, itf.getOwningContext());
					Set<ContextChain> impactedEnd2 = new HashSet<>();
					if (end2!=null) {
						if (!end2.isAbstract()) impactedEnd2.add(end2);
						for (ContextChain c : end2.getDependentContexts()) {
							if (!c.isAbstract() && scope.contains(c)) impactedEnd2.add(c);
						}
					}
					
					for (ContextChain c1 : impactedEnd1) {
						for (ContextChain c2 : impactedEnd2) {
							
							connectionMap.computeIfAbsent(c1, k -> new HashSet<>()).add(c2);
							connectionMap.computeIfAbsent(c2, k -> new HashSet<>()).add(c1);
							
							for (FlowUsage fl : flows) {
								
								ContextChain tempc1 = c1;
								ContextChain tempc2 = c2;
								if (((InterfaceDefinition) fl.getOwner()).getInterfaceEnd().get(0) != fl.getSource().get(0)) {
									tempc1 = c2;
									tempc2 = c1;
								}
																
								Feature item = fl.getSourceOutputFeature();
								for (Feature realItem : FeatureUtil.getSubsettedFeaturesOf(item)) {
									if (inputResources.contains(realItem.eResource()) && realItem instanceof ItemUsage realItemUsage) {
										item = realItemUsage;	
										break;
									}
								}
								ContextChain itemSource = tempc1.findFeatureCtxt(item);
								ContextChain itemTarget = tempc2.findFeatureCtxt(item);
																
								if (itemSource != null && itemTarget != null) flowMap.computeIfAbsent(item,  k -> new HashMap<>()).computeIfAbsent(itemSource, k -> new HashSet<>()).add(itemTarget);
								
								Set<ContextChain> ends = endMap.computeIfAbsent(item, k -> new HashSet<>());
								Set<ContextChain> sources = sourceMap.computeIfAbsent(item, k -> new HashSet<>());
								if(ends.contains(itemSource)) ends.remove(itemSource);
								else  sources.add(itemSource); 
								if(sources.contains(itemTarget)) sources.remove(itemTarget);
								else ends.add(itemTarget);
							}
						}
					}
				}
			}
		}
	}

	private ContextChain resolveEnd(Feature end, ContextChain scope) {
		EList<Feature> chain = end.getChainingFeature();
		if (!chain.isEmpty()) {
			for (Feature f : chain) {
				Boolean found  = false;
				for (ContextChain c : scope.getOwnedContexts()) {
					if (FeatureUtil.getAllRedefinedFeaturesOf(c.getFeature()).contains(f)) {
						scope = c;
						found = true;
						break;
					}
				}
				if (!found) {
					scope = null;
					break;
				}
			}
			return scope;
		} else {
			for (ContextChain c : scope.getOwnedContexts()) {
				if (FeatureUtil.getAllRedefinedFeaturesOf(c.getFeature()).contains(end)) return c;
			}
		}
		return null;
	}

	
	public Map<Package,Map<Integer,Set<ContextChain>>> getContextMaps() {
		return contextMaps;
	}
	
	public ContextChain getContextByName(Package pckg, String name) {
		ContextChain focus = null;
		List<String> searchNames = List.of(name.split("\\."));
		Set<ContextChain> searchSet = contextMaps.get(pckg).get(1);
		searchSteps  : for (int i = 0; i < searchNames.size(); i++) {
			for (ContextChain c : searchSet) {
				if (searchNames.get(i).equals(c.getFeature().getName())) {
					focus = c;
					searchSet = c.getOwnedContexts();
					continue searchSteps;
				}
			}
			return null;
		}
		
		return focus;
	}
	
	public Map<Package,Map<ContextChain,Set<ContextChain>>> getConnectionMaps() {
		return connectionMaps;
	}
	
	public Map<Package, Map<Feature, Map<ContextChain, Set<ContextChain>>>> getFlowMaps() {
		return flowMaps;
	}
	
	public Map<Package,Map<Feature,Set<ContextChain>>> getSourceMaps() {
		return sourceMaps;
	}
	
	public Set<ContextChain> getFlowPorts(Package pckg, Feature item) {
		Set<ContextChain> ports = new HashSet<>();
		Map<ContextChain, Set<ContextChain>> map = flowMaps.get(pckg).get(item);
		for (ContextChain c : map.keySet()) {
			ports.add(c.getOwningContext());
			for (ContextChain c2 : map.get(c)) ports.add(c2.getOwningContext());
		}
		return ports;
	}
	
}