package org.modelcatalogue.core.xml

import grails.gorm.DetachedCriteria
import org.modelcatalogue.core.*

abstract class CatalogueElementPrintHelper<E extends CatalogueElement> {

    private static <E extends CatalogueElement> CatalogueElementPrintHelper<E> get(Class<E> type) {
        // TODO: pool helpers as they are stateless
        if (MeasurementUnit.isAssignableFrom(type)) {
            return new MeasurementUnitPrintHelper() as CatalogueElementPrintHelper<E>
        }
        if (DataType.isAssignableFrom(type)) {
            return new DataTypePrintHelper() as CatalogueElementPrintHelper<E>
        }
        if (ValueDomain.isAssignableFrom(type)) {
            return new ValueDomainPrintHelper() as CatalogueElementPrintHelper<E>
        }
        if (DataElement.isAssignableFrom(type)) {
            return new DataElementPrintHelper() as CatalogueElementPrintHelper<E>
        }
        if (Model.isAssignableFrom(type)) {
            return new ModelPrintHelper() as CatalogueElementPrintHelper<E>
        }
        if (Classification.isAssignableFrom(type)) {
            return new ClassificationPrintHelper() as CatalogueElementPrintHelper<E>
        }
        if (Asset.isAssignableFrom(type)) {
            return new AssetPrintHelper() as CatalogueElementPrintHelper<E>
        }
        throw new IllegalArgumentException("Not yet implemented for $type")
    }

    static void printElement(theMkp, CatalogueElement element, PrintContext context, Relationship rel, String elementName = null) {
        CatalogueElementPrintHelper helper = get(element.class)
        if (!elementName) {
            elementName = helper.topLevelName
        }

        if (element instanceof Classification) {
            if (context.currentClassification) {
                theMkp."${elementName}"(ref(element, context)) {
                    processRelationshipMetadata(theMkp, context, rel)
                }
                return
            }
            context.currentClassification = element
            context.typesUsed << 'classification'
        }

        if (context.wasPrinted(element)) {
            theMkp."${elementName}"(ref(element, context)) {
                processRelationshipMetadata(theMkp, context, rel)
            }
            return
        }

        context.markAsPrinted(element)

        theMkp."${elementName}"(helper.collectAttributes(element, context)) {
            helper.processElements(theMkp, element, context, rel)
        }

        if (element instanceof Classification) {
            context.currentClassification = null
        }
    }


    Map<String, Object> collectAttributes(E element, PrintContext context) {
        Map<String, Object> attrs = [name: element.name]

        if (element.classifications && !(context.currentClassification in element.classifications)) {
            attrs.classification = element.classifications.first().name
        }

        if (element.hasModelCatalogueId()) {
            attrs.id   = element.modelCatalogueId
            if (!context.noHref) {
                attrs.href = element.getDefaultModelCatalogueId(!context.idIncludeVersion)
            }
        } else {
            attrs.id = element.getDefaultModelCatalogueId(!context.idIncludeVersion)
        }

        if (element.status != org.modelcatalogue.core.api.ElementStatus.FINALIZED) {
            if (element.status in [org.modelcatalogue.core.api.ElementStatus.DRAFT, org.modelcatalogue.core.api.ElementStatus.DEPRECATED]) {
                attrs.status = element.status
            } else {
                throw new IllegalArgumentException("Cannot print ${element.getClass().simpleName} with status $element.status")
            }
        }

        attrs
    }

    void processElements(theMkp, E element, PrintContext context, Relationship relationship) {
        processRelationshipMetadata(theMkp, context, relationship)

        if (element.description) {
            theMkp.description element.description
        }
        for (Relationship rel in element.isBasedOnRelationships) {
            theMkp.basedOn(ref(rel.source, context)) {
                processRelationshipMetadata(theMkp, context, rel)
            }
        }
        for (Relationship rel in element.relatedToRelationships) {
            CatalogueElement other = rel.source == element ? rel.destination : rel.source
            theMkp.relatedTo(ref(other, context)) {
                processRelationshipMetadata(theMkp, context, rel)
            }
        }
        for (Relationship rel in element.isSynonymForRelationships) {
            CatalogueElement other = rel.source == element ? rel.destination : rel.source
            theMkp.synonym(ref(other, context)){
                processRelationshipMetadata(theMkp, context, rel)
            }
        }
        if (element.ext) {
            theMkp.extensions {
                for (Map.Entry<String, String> entry in element.ext.entrySet()) {
                    extension(key: entry.key, entry.value)
                }
            }
        }

        List<Relationship> outgoing = restOfRelationships(Relationship.where { source == element }).list()
        List<Relationship> incoming = restOfRelationships(Relationship.where { destination == element }).list()

        if (outgoing || incoming) {
            theMkp.relationships {
                for (Relationship rel in outgoing){
                    to(relationshipAttrs(rel, true, context)) {
                        processRelationshipMetadata(theMkp, context, rel)
                    }
                }

                for (Relationship rel in incoming){
                    from(relationshipAttrs(rel, false, context)) {
                        processRelationshipMetadata(theMkp, context, rel)
                    }
                }
            }
        }
    }

    static void processRelationshipMetadata(theMkp, PrintContext context, Relationship rel) {
        if (!rel) {
            return
        }
        context.typesUsed << rel.relationshipType.name
        if (rel.ext) {
            theMkp.metadata {
                for (Map.Entry<String, String> entry in rel.ext.entrySet()) {
                    extension(key: entry.key, entry.value)
                }
            }
        }
        if (rel.archived) {
            theMkp.archived true
        }
    }

    static DetachedCriteria<Relationship> restOfRelationships(DetachedCriteria<Relationship>  criteria) {
        criteria.not {
            'in' 'relationshipType', ['classification', 'hierarchy', 'containment', 'supersession', 'base', 'relatedTo', 'synonym'].collect { RelationshipType.readByName(it) }
        }
        criteria.'eq' 'archived', false
    }

    static Map<String, Object> ref(CatalogueElement element, PrintContext context) {
        if (element.hasModelCatalogueId()) {
            if (context.noHref) {
                return [ref: element.modelCatalogueId]
            }
            return [ref: element.modelCatalogueId, href: element.getDefaultModelCatalogueId(!context.idIncludeVersion)]
        }

        if (element.classifications) {
            if (context.currentClassification in element.classifications) {
                return [name: element.name]
            } else {
                return [name: element.name, classification: element.classifications.first().name]
            }
        }


        return [ref: element.getDefaultModelCatalogueId(!context.idIncludeVersion)]
    }

    static Map<String, Object> relationshipAttrs(Relationship relationship, boolean outgoing, PrintContext context) {
        Map<String, Object> ret = ref(outgoing ? relationship.destination : relationship.source, context)
        ret.relationshipType = relationship.relationshipType.name
        if (!ret.ref) {
            if (outgoing && relationship.destination.getClass() != relationship.relationshipType.destinationClass) {
                ret.type = shortNameForKnown(relationship.destination.getClass())
            }
            if (!outgoing && relationship.source.getClass() != relationship.relationshipType.sourceClass) {
                ret.type = shortNameForKnown(relationship.destination.getClass())
            }
        }
        return ret
    }

    static String shortNameForKnown(Class type) {
        if (type == EnumeratedType) {
            return 'dataType'
        }
        if (type in [Classification, Model, DataElement, ValueDomain, DataType, MeasurementUnit]) {
            String simpleName = type.simpleName
            return simpleName[0].toLowerCase() + simpleName[1..-1]
        }
        return type.name
    }

    abstract String getTopLevelName()


}
