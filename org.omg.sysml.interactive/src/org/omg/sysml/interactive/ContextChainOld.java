package org.omg.sysml.interactive;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.omg.sysml.lang.sysml.Feature;
import org.omg.sysml.util.FeatureUtil;

public class ContextChainOld {

    private EList<Feature> features = new BasicEList<>();
    private Set<ContextChainOld> dependentContexts = new HashSet<>();
    private Set<ContextChainOld> dependingContexts = new HashSet<>();
    private ContextChainOld owningContext = null;
    private Set<ContextChainOld> ownedContexts = new HashSet<>();
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
        ctxtString += "\n features= ";
        for (Feature f : features) ctxtString += "  " + f.getQualifiedName();
        //ctxtString += "\n dependent: ";
        //for (ContextChain c : dependentContexts) ctxtString += c.getId() + " ";
        //ctxtString += "\n depending: ";
        //for (ContextChain c : dependingContexts) ctxtString += c.getId() + " ";
        return ctxtString+"\n";
    }

    public ContextChainOld(Feature feature) {
    	this.features.add(feature);
    	this.id = ID_COUNTER++;
    }

	public ContextChainOld(ContextChainOld ctxt, Feature feature) {
		this.owningContext = ctxt;
		ctxt.addOwnedContext(this);
		
		this.isAbstract = ctxt.isAbstract();
		this.features.addAll(ctxt.getFeatures());
		this.features.add(feature);
    	this.id = ID_COUNTER++;
	}

	public void addFeature(Feature feature) {
        features.add(feature);
    }

    public EList<Feature> getFeatures() {
        return features;
    }    
    public Feature getTail() {
    	return features.getLast();
    }
    public int size() {
        return features.size();
    }

    public Set<ContextChainOld> getDependentContexts() {
		return dependentContexts;
	}

	public void addDependentContext(ContextChainOld dependentContext) {
		this.dependentContexts.add(dependentContext);
		dependentContext.addDependingContext(this);
	}

	public Set<ContextChainOld> getOwnedContexts() {
		return ownedContexts;
	}
	public Set<ContextChainOld> getRecursivelyOwnedContexts() {
		Set<ContextChainOld> allOwned = new HashSet<>();
		for (ContextChainOld c : ownedContexts) {
			allOwned.add(c);
			allOwned.addAll(c.getRecursivelyOwnedContexts());
		}
		return allOwned;
	}

	public void addOwnedContext(ContextChainOld ownedContext) {
		this.ownedContexts.add(ownedContext);
	}

	public ContextChainOld getOwningContext() {
		return owningContext;
	}

	public Set<ContextChainOld> getDependingContexts() {
		return dependingContexts;
	}

	public void addDependingContext(ContextChainOld dependingContext) {
		this.dependingContexts.add(dependingContext);
	}

	public int getId() {
		return id;
	}

	public boolean isAbstract() {
		return isAbstract;
	}
	public void setAbstract(boolean b) {
		this.isAbstract = b;		
	}
	
	public Feature findFeature(Feature feature) {
		for (ContextChainOld c_son : ownedContexts) {
			Feature tail = c_son.getTail();
			if (tail==feature || FeatureUtil.getSubsettedFeaturesOf(tail).contains(feature)) {
				return tail;
			}
		}
		return null;
	}	
}
