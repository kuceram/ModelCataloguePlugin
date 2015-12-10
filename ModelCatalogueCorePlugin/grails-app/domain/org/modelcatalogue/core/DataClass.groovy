package org.modelcatalogue.core

import org.modelcatalogue.core.publishing.Publisher
import org.modelcatalogue.core.publishing.PublishingChain
import org.modelcatalogue.core.util.FriendlyErrors
import org.modelcatalogue.core.util.Legacy

class DataClass extends CatalogueElement {

    static relationships = [
            incoming: [hierarchy: 'childOf'],
            outgoing: [containment: 'contains', hierarchy: 'parentOf']
    ]

    @Override
    protected PublishingChain preparePublishChain(PublishingChain chain) {
        super.preparePublishChain(chain)
                .add(this.contains)
                .add(this.parentOf)
    }

    @Override
    protected PublishingChain prepareDraftChain(PublishingChain chain) {
        chain.add(this.childOf).add(this.dataModel).add(referringDataTypes)
    }

    @Override
    void setModelCatalogueId(String mcID) {
        super.setModelCatalogueId(Legacy.fixModelCatalogueId(mcID))
    }

    @Override
    String getDefaultModelCatalogueId(boolean withoutVersion = false) {
        // TODO: remove when the class is renamed
        return Legacy.fixModelCatalogueId(super.getDefaultModelCatalogueId(withoutVersion))
    }


    List<ReferenceType> getReferringDataTypes() {
        if (!readyForQueries) {
            return []
        }
        return ReferenceType.findAllByDataClass(this)
    }

    Long countReferringDataTypes() {
        if (!readyForQueries) {
            return 0
        }
        return ReferenceType.countByDataClass(this)
    }

    DataClass removeFromReferringDataTypes(ReferenceType domain) {
        domain.dataClass = null
        FriendlyErrors.failFriendlySave(domain)
        this
    }


}