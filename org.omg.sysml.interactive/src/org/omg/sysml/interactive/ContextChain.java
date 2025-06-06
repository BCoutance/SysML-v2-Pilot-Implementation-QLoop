package org.omg.sysml.interactive;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.BasicEList;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.util.FeatureUtil;

public class ContextChain {

    private Feature feature;
	private int size;
    private Set<ContextChain> dependentContexts = new HashSet<>();
    private Set<ContextChain> dependingContexts = new HashSet<>();
    private ContextChain owningContext = null;
    private Set<ContextChain> ownedContexts = new HashSet<>();
    private boolean isAbstract = false;
    
    private static int ID_COUNTER = 0;
    private final int id;
    
    public static void resetIdCounter() {
        ID_COUNTER = 0;
    }
    
    
    @Override
    public String toString() {
        String ctxtString =  "ContextChain " + getId();
        if (isAbstract) ctxtString += " Abstract";
        ctxtString += "\n features : ";
        for (Feature f : this.getFeatureList()) ctxtString += "  "+f.getQualifiedName();
        //ctxtString += "\n dependent: ";
        //for (ContextChain c : dependentContexts) ctxtString += c.getId() + " ";
        //ctxtString += "\n depending: ";
        //for (ContextChain c : dependingContexts) ctxtString += c.getId() + " ";
        return ctxtString+"\n";
    }

    public ContextChain(Feature feature) {
    	this.id = ID_COUNTER++;
    	this.feature = feature;
    	this.size = 1;
    }

	public ContextChain(ContextChain ctxt, Feature feature) {
    	this.id = ID_COUNTER++;
    	this.feature = feature;
		this.size = ctxt.size() + 1;

		this.owningContext = ctxt;
		ctxt.addOwnedContext(this);		
		this.isAbstract = ctxt.isAbstract();
	}

    public Feature getFeature() {
        return feature;
    }
    
	public List<Feature> getFeatureList() {
		List<Feature> list = new BasicEList<>();
        ContextChain ctxt = this;
		while (ctxt != null) {
        	list.addFirst(ctxt.getFeature());
        	ctxt = ctxt.getOwningContext();
        }
		return list;
	}	

    public int size() {
        return size;
    }

    public Set<ContextChain> getDependentContexts() {
		return dependentContexts;
	}

	public void addDependentContext(ContextChain dependentContext) {
		this.dependentContexts.add(dependentContext);
		dependentContext.addDependingContext(this);
	}

	public Set<ContextChain> getOwnedContexts() {
		return ownedContexts;
	}
	public Set<ContextChain> getRecursivelyOwnedContexts() {
		Set<ContextChain> allOwned = new HashSet<>();
		for (ContextChain c : ownedContexts) {
			allOwned.add(c);
			allOwned.addAll(c.getRecursivelyOwnedContexts());
		}
		return allOwned;
	}

	public void addOwnedContext(ContextChain ownedContext) {
		this.ownedContexts.add(ownedContext);
	}

	public ContextChain getOwningContext() {
		return owningContext;
	}

	public Set<ContextChain> getDependingContexts() {
		return dependingContexts;
	}

	public void addDependingContext(ContextChain dependingContext) {
		this.dependingContexts.add(dependingContext);
	}

	public boolean isAbstract() {
		return isAbstract;
	}
	public void setAbstract(boolean b) {
		this.isAbstract = b;	
	}
	
	public ContextChain findFeatureCtxt(Feature feature) {
		for (ContextChain contextChain : ownedContexts) {
			Feature f = contextChain.getFeature();
			int i =0;
		}
		ContextChain c_son = ownedContexts.stream()
				.filter(c -> c.getFeature()==feature || FeatureUtil.getSubsettedFeaturesOf(c.getFeature()).contains(feature) || FeatureUtil.getAllRedefinedFeaturesOf(c.getFeature()).contains(feature))
				.findFirst().orElse(null);
		return c_son;
	}
	
	public int getId() {
		return id;
	}
}
