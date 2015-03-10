/** Generated automatically from dataType. Do not edit this file manually! */
    (function (window) {
        window['fixtures'] = window['fixtures'] || {};
        var fixtures = window['fixtures']
        fixtures['dataType'] = fixtures['dataType'] || {};
        var dataType = fixtures['dataType']

        window.fixtures.dataType.incoming6 = {
   "total": 11,
   "previous": "/dataType/36/incoming/relationship?max=2&offset=8",
   "page": 2,
   "itemType": "org.modelcatalogue.core.Relationship",
   "listType": "org.modelcatalogue.core.util.Relationships",
   "next": "",
   "list": [{
      "id": 1874,
      "direction": "destinationToSource",
      "removeLink": "/dataType/36/incoming/relationship",
      "relation": {
         "id": 29,
         "outgoingRelationships": {
            "count": 1,
            "itemType": "org.modelcatalogue.core.Relationship",
            "link": "/enumeratedType/29/outgoing"
         },
         "valueDomains": {
            "count": 0,
            "itemType": "org.modelcatalogue.core.ValueDomain",
            "link": "/enumeratedType/29/valueDomain"
         },
         "description": null,
         "name": "etTest8",
         "link": "/enumeratedType/29",
         "elementTypeName": "Enumerated Type",
         "elementType": "org.modelcatalogue.core.EnumeratedType",
         "incomingRelationships": {
            "count": 0,
            "itemType": "org.modelcatalogue.core.Relationship",
            "link": "/enumeratedType/29/incoming"
         },
         "version": 1,
         "enumerations": {
            "m2m": "test2",
            "m8m": "test8"
         }
      },
      "type": {
         "id": 3,
         "sourceClass": "org.modelcatalogue.core.CatalogueElement",
         "sourceToDestination": "relates to",
         "destinationClass": "org.modelcatalogue.core.CatalogueElement",
         "name": "relationship",
         "link": "/relationshipType/3",
         "elementTypeName": "Relationship Type",
         "elementType": "org.modelcatalogue.core.RelationshipType",
         "destinationToSource": "is relationship of",
         "version": 0
      }
   }],
   "offset": 10,
   "success": true,
   "size": 1
}

    })(window)