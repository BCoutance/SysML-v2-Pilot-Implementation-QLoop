package org.omg.sysml.interactive;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.omg.sysml.execution.expressions.ExpressionEvaluator;
import org.omg.sysml.util.EvaluationUtil;
import org.omg.sysml.lang.sysml.Element;
import org.omg.sysml.lang.sysml.Expression;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.lang.sysml.FeatureMembership;
import org.omg.sysml.lang.sysml.FeatureReferenceExpression;
import org.omg.sysml.lang.sysml.FeatureTyping;
import org.omg.sysml.lang.sysml.FeatureValue;
import org.omg.sysml.lang.sysml.InterfaceUsage;
import org.omg.sysml.lang.sysml.InvocationExpression;
import org.omg.sysml.lang.sysml.Membership;
import org.omg.sysml.lang.sysml.PortUsage;
import org.omg.sysml.lang.sysml.Redefinition;
import org.omg.sysml.lang.sysml.ReferenceUsage;
import org.omg.sysml.lang.sysml.Subsetting;
import org.omg.sysml.lang.sysml.SysMLFactory;
import org.omg.sysml.lang.sysml.Type;
import org.omg.sysml.lang.sysml.util.SysMLLibraryUtil;
import org.omg.sysml.util.ExpressionUtil;
import org.omg.sysml.util.FeatureUtil;
import org.omg.sysml.util.TypeUtil;

public class SysMLPortConnection {
		
	public static Map<Feature,Set<Feature>> portChildren = new HashMap<Feature,Set<Feature>>();
	public static Map<Feature,Set<Feature>> connectedPorts = new HashMap<Feature,Set<Feature>>();
	
	private static Resource resource = null;
	
	public static void initCaches() {
		portChildren.clear();
		connectedPorts.clear();
	}

// TODO Iterate over loaded resource and create the real features for all the content
//  	Only create redef of features owned by an input resource
//		While doing it, list all interfaces (but do not resolve their ports) 
//		and list all ports and their inheritance relations (when to do it, during redef creation or after ?)
//		Then create complete the connected ports map with interfaces list and inheritance map 
//		Use this map to populate "InterfacingPorts"
	
	public static void resolvePortConnection(Resource m0resource, org.omg.sysml.lang.sysml.Package pckg) {
		resource = m0resource;
		for (Element element : pckg.getOwnedElement()) {
			if (element instanceof Feature feature) {
				//gatherPorts(pckg, feature);
				if (feature instanceof InterfaceUsage itf) addInterface(itf);
				find_ITF(feature);
			}
		}
		for (Feature port : connectedPorts.keySet()) {
			setInterfacingPorts(port, connectedPorts.get(port));		
		}
	}


	private static void find_ITF(Feature sourceF) {
		for (Feature f : EvaluationUtil.getFeature(sourceF)) {
			if (f.eResource() == resource){
				if (f instanceof InterfaceUsage itf) addInterface(itf);
				find_ITF(f);
			}
		}
	}

	private static void addInterface(InterfaceUsage itf) {
		// Create redef of itf inside the feature
		EList<Element> source = itf.getSource();    
		EList<Element> target = itf.getTarget();
        if (!source.isEmpty() && !target.isEmpty()) {
        	if (source.get(0) instanceof Feature port1 && target.get(0) instanceof Feature port2) {
        		if (!(port1 instanceof PortUsage)) {
        			Expression port1Expr = EvaluationUtil.expressionFor(port1);
        			port1 = (Feature) ExpressionEvaluator.INSTANCE.evaluate(port1Expr, null).get(0);
        		}
        		if (!(port2 instanceof PortUsage)) {
        			Expression port2Expr = EvaluationUtil.expressionFor(port2);
        			port2 = (Feature) ExpressionEvaluator.INSTANCE.evaluate(port2Expr, null).get(0);
        		}
           		//addConnection(port1, port2);
        		connectedPorts.computeIfAbsent(port1, k -> new HashSet<>()).add(port2);
        		connectedPorts.computeIfAbsent(port2, k -> new HashSet<>()).add(port1);
        	}
        }  
    }
	
	
	private static void gatherPorts(Element father, Feature feature) {
		if (!feature.isAbstract() && feature.eResource() == resource) {
			
			if (feature instanceof PortUsage portFeature && !feature.isEnd()) {
				//for (Subsetting s : feature.getOwnedSubsetting()) { // differentiate subsetting and refefinitons and remove interfaces endports ?
				for (Feature e : FeatureUtil.getSubsettedFeaturesOf(feature)){
				//for (Element e : s.getTarget()) { 
						if (!e.getDeclaredName().matches("ownedPorts")) {
						//Expression port2Expr = EvaluationUtil.expressionFor(e);
						//Feature port2 = (Feature) ExpressionEvaluator.INSTANCE.evaluate(port2Expr, null).get(0);
							portChildren.computeIfAbsent(e, k -> new HashSet<>()).add(portFeature);	
						}
					//}
				}
				for (Feature f: FeatureUtil.getAllRedefinedFeaturesOf(feature)) {
					if (f != feature && f instanceof PortUsage redefinedPort) {
						portChildren.computeIfAbsent(redefinedPort, k -> new HashSet<>()).add(feature);
					}
				}
			} 
			//else {
			//	Set<Feature> allRedefinedFeaturesOf = FeatureUtil.getAllRedefinedFeaturesOf(feature);
			//	for (Feature f: allRedefinedFeaturesOf) {
			//		if (f != feature && f instanceof PortUsage redefinedPort) {
			//			portChildren.computeIfAbsent(redefinedPort, k -> new HashSet<>()).add(feature);
			//		}
			//	}
			//}
			for (Feature f: EvaluationUtil.getFeature(feature)) {
				gatherPorts(feature, f);
			}			
		}
	}

	public static void resolvePortConnectionOld(List<Resource> inputResources, Resource resource) {
		// resolving the ports inheritance DAG
		for (Resource r : inputResources) { //TODO enter through namespace instead of iterator
			TreeIterator<Object> iterator = EcoreUtil.getAllContents(r, true);
			while (iterator.hasNext()) {
				if (iterator.next() instanceof Feature feature) gatherPortsOld(feature.getOwner(), feature, inputResources);
			}
			//portChildren.clear();
		}
		// detecting interfaces and building connections graph

		for (Object content : resource.getContents()) {			
			if (content instanceof Feature contentF) {
				find_ITFOld(inputResources, contentF);
			}
			if (content instanceof InterfaceUsage itf) ;//addInterface(itf, resource);
		}
		
		// set interfacingPorts for each port
		for (Feature port1 : connectedPorts.keySet()) setInterfacingPorts(port1, connectedPorts.get(port1));		
	}

	private static void find_ITFOld(List<Resource> inputResources, Feature sourceF) {
		for (Feature f : EvaluationUtil.getFeature(sourceF)) {
			if (inputResources.contains(f.eResource())){
				if (f instanceof InterfaceUsage itf) addInterfaceOld(itf, sourceF);
				find_ITFOld(inputResources, f);
			}
		}
	}
	
	private static void gatherPortsOld(Element father, Feature feature, List<Resource> inputResources) { //if necessary add a memory of already seen (father, feature)
		if (inputResources.contains(feature.eResource())) {
			if (feature instanceof ReferenceUsage refFeature) {
				if (refFeature.getOwner() == father) {
					for (Subsetting s : refFeature.getOwnedSubsetting()) {
						for (Element e : s.getTarget()) {
							if (e instanceof PortUsage redefinedPort) {
								portChildren.computeIfAbsent(redefinedPort, k -> new HashSet<>()).add(refFeature); // addPortChild(redefinedPort, refFeature);
							}
						}
					}
				} else {
					Feature reference = EvaluationUtil.getTypeFeatureFor(refFeature, (Feature) father);
					portChildren.computeIfAbsent(refFeature, k -> new HashSet<>()).add(reference);	
					//addPortChild(refFeature, reference);;
				}
			}
			else if (feature instanceof PortUsage portFeature) {
				if (portFeature.getOwner() == father) {
					if (portFeature.getDeclaredName()!=null) {
						for (Subsetting s : feature.getOwnedSubsetting()) {
							for (Element e : s.getTarget()) { 
								Expression port2Expr = EvaluationUtil.expressionFor(e);
								Feature port2 = (Feature) ExpressionEvaluator.INSTANCE.evaluate(port2Expr, null).get(0);
								portChildren.computeIfAbsent(port2, k -> new HashSet<>()).add(portFeature);	
								// addPortChild(port2, portFeature);
							}
						}
					}
				}
				else {
					Feature reference = EvaluationUtil.getTypeFeatureFor(portFeature, (Feature) father);
					portChildren.computeIfAbsent(portFeature, k -> new HashSet<>()).add(reference);	
					// addPortChild(portFeature, reference);
				}
			}
			EList<Feature> features = EvaluationUtil.getFeature(feature);
			int i = 0;
			while (i < features.size()) {
			    Feature f = features.get(i);
			    gatherPortsOld(feature, f, inputResources);
			    i++; 
			}
		}
	}
	
//	private static void addPortChild(Feature father, Feature son) {
//		portChildren.computeIfAbsent(father, k -> new HashSet<>()).add(son);	
//	}
	
	private static void addInterfaceOld(InterfaceUsage itf, Feature realOwner) {
		// Create redef of itf inside the feature
		EList<Element> source = itf.getSource();    
		EList<Element> target = itf.getTarget();
        if (!source.isEmpty() && !target.isEmpty()) {
        	if (source.get(0) instanceof Feature port1 && target.get(0) instanceof Feature port2) {
        		if (!(port1 instanceof PortUsage)) {
        			Expression port1Expr = EvaluationUtil.expressionFor(port1);
        			port1 = (Feature) ExpressionEvaluator.INSTANCE.evaluate(port1Expr, null).get(0);
        		}
        		if (!(port2 instanceof PortUsage)) {
        			Expression port2Expr = EvaluationUtil.expressionFor(port2);
        			port2 = (Feature) ExpressionEvaluator.INSTANCE.evaluate(port2Expr, null).get(0);
        		}
           		addConnectionOld(port1, port2);
        	}
        }  
    }
	
	private static void addConnectionOld(Feature port1, Feature port2) {
		// adding both ports to each other connectedPorts
		connectedPorts.computeIfAbsent(port1, k -> new HashSet<>()).add(port2);
		connectedPorts.computeIfAbsent(port2, k -> new HashSet<>()).add(port1);
		
		// creating a connection between both ports and each other children (recursive)
		if (portChildren.containsKey(port1)) {
			for (Feature child : portChildren.get(port1)) {
				if (!connectedPorts.get(port2).contains(child)) addConnectionOld(child, port2);
			}
		}
		if (portChildren.containsKey(port2)) {
			for (Feature child : portChildren.get(port2)) {
				if (!connectedPorts.get(port1).contains(child)) addConnectionOld(child, port1);			
			}
		}
	}
	
	private static void setInterfacingPorts(Feature basePort, Set<Feature> cntPorts) {
		EList<Feature> features = EvaluationUtil.getFeature(basePort);
		for (Feature f : features) {
			if (f.getDeclaredName() != null && f.getDeclaredName().equals("interfacingPorts")) { //f.getQualifiedName().equals("Ports::Port::interfacingPorts"
				PortUsage itfPorts = (PortUsage) EvaluationUtil.createRedef(basePort, f);
				itfPorts.setDeclaredName(itfPorts.getDeclaredName()+"_populated");
				//ReferenceUsage referenceUsage = createRedefRef(basePort, f, "interfacingPorts_local_ctxt");
				EList<Element> cntPortsList = new BasicEList<>(cntPorts);
				Expression listExpr = EvaluationUtil.expressionFor(cntPortsList, basePort);
				//linkValue(referenceUsage,listExpr);
				linkValue(itfPorts,listExpr);
				features.remove(f); 
				features.addFirst(itfPorts); 
				break;
			}
		}
	}
	
	private static void setInterfacingPortsOld(Feature basePort, Feature cntPort) {
		// Work on order of creation to solve conflicts with manual redefinition in SysML model
		
		// Create an expression pointing at cntPort 				
		Expression featRefExpr = EvaluationUtil.expressionFor(cntPort);
		
		EList<Feature> features = EvaluationUtil.getFeature(basePort);
		for (Feature f : features) {
			if (f.getDeclaredName() != null && f.getDeclaredName().equals("interfacingPorts_ref")) {				
				if (f.getOwner().getQualifiedName().equals("Ports::Port")) { // if interfacingPorts was not elsewhere redefined 
					ReferenceUsage referenceUsage = createRedefRef(basePort, f, "interfacingPorts_local_ctxt");
					linkValue(referenceUsage,featRefExpr);
				}				
				break;
			} 
			if (f.getDeclaredName() != null && f.getDeclaredName().equals("interfacingPorts_local_ctxt")) { // if interfacingPorts was already redefined 
				
				if (f.getOwner() == basePort) {
					Expression fValueExpression = FeatureUtil.getValueExpressionFor(f);					
					// "interfacing ports" has a value (i-e one or more other ports are already connected), create a collection : (cntPort, other ports) 
					InvocationExpression listExpr = createListExpr(featRefExpr, fValueExpression);
					// removing former value expression of "interfacing ports"
					f.getOwnedRelationship().remove(FeatureUtil.getValuationFor(f));						
					// connect the new expression to "interfacing ports" redefinition feature through a feature value
					linkValue(f, listExpr);	
				
				} else { // dealing with an inherited populating of interfacingPorts by setConnectedPort. Will not appear in current cache exploration
					ReferenceUsage referenceUsage = createRedefRef(basePort, f, "interfacingPorts_local_ctxt");
					
					// "interfacingPorts_local_ctxt" has a value (i-e one or more other ports are already connected to basePort), 
					// we do not want to overwrite it, so we create a reference to it
					Expression fValueExpression = FeatureUtil.getValueExpressionFor(f);
					
					// equivalent to expressionFor() but with another expression as target
					FeatureReferenceExpression featRefExpr2fVal = SysMLFactory.eINSTANCE.createFeatureReferenceExpression();
					Membership referentmembership2fVal = SysMLFactory.eINSTANCE.createMembership();
					referentmembership2fVal.setMemberElement(fValueExpression);
					featRefExpr2fVal.getOwnedRelationship().add(referentmembership2fVal);

					// create a collection : (cntPort, other ports) 
					InvocationExpression listExpr = createListExpr(featRefExpr, featRefExpr2fVal);
					linkValue(referenceUsage, listExpr);
				}
				break;
			}
		}
	}

	private static ReferenceUsage createRedefRef(Feature targetFeature, Feature redefinedFeature, String name) {
		// create redefinition of redefinedFeature
		Redefinition redefinition = SysMLFactory.eINSTANCE.createRedefinition();
		redefinition.setRedefinedFeature(redefinedFeature);
		
		// create a reference to this redefinition
		ReferenceUsage referenceUsage = SysMLFactory.eINSTANCE.createReferenceUsage();
		referenceUsage.setDeclaredName(name); 
		referenceUsage.getInheritedMembership().addAll(redefinedFeature.getFeatureMembership());
		referenceUsage.getOwnedRelationship().add(redefinition);
		FeatureUtil.getAllRedefinedFeaturesOf(referenceUsage).addAll(FeatureUtil.getAllRedefinedFeaturesOf(redefinedFeature)); // to add in EvaluationUtil.java ?
							
		// connect this reference to targetFeature 
		FeatureMembership membership = SysMLFactory.eINSTANCE.createFeatureMembership();
		membership.setOwnedMemberFeature(referenceUsage);
		membership.setOwningType(targetFeature);
		targetFeature.getOwnedRelationship().addFirst(membership);
		
		// replace the redefinedFeature by the reference in targetFeature's features 
		EList<Feature> features = EvaluationUtil.getFeature(targetFeature);
		features.remove(redefinedFeature);
		features.addFirst(referenceUsage);
		return referenceUsage;
	}

	private static InvocationExpression createListExpr(Expression featRefExpr, Expression fValueExpression) { 
		//TODO recursive with list as input 
		
		InvocationExpression listExpr = SysMLFactory.eINSTANCE.createInvocationExpression();
		Type listOp = SysMLLibraryUtil.getLibraryType(featRefExpr, ExpressionUtil.getOperatorQualifiedNames(","));

		// connecting the new expression which will represent the collection and its operator ","
		FeatureTyping typing = SysMLFactory.eINSTANCE.createFeatureTyping();
		listExpr.getOwnedRelationship().add(typing);
		typing.setTypedFeature(listExpr);
		typing.setType(listOp);

		// filling the new collection with arguments
		TypeUtil.addOwnedParameterTo(listExpr, featRefExpr);
		TypeUtil.addOwnedParameterTo(listExpr, fValueExpression);
		return listExpr;
	}

	private static void linkValue(Feature feature, Expression valueExpr) {
		FeatureValue featureValue = SysMLFactory.eINSTANCE.createFeatureValue();
		featureValue.getOwnedRelatedElement().add(valueExpr);	
		feature.getOwnedRelationship().add(featureValue);
	}
		
}