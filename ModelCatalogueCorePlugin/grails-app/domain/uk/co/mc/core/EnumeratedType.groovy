package uk.co.mc.core

/*
* Enumerated Types are data types that contain a list of enumerated values
* i.e. ['politics', 'history', 'science']
* Enumerated Types are used by Value Domains (please see ValueDomain and Usance)
* i.e. ValueDomain subjects uses EnumeratedType enumerations ['politics', 'history', 'science']
* */

class EnumeratedType extends DataType{

    Map <String, String> enumerations

    static constraints = {
        enumerations minSize: 2, nullable: false
    }
}
