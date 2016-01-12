package org.modelcatalogue.integration.excel

import org.apache.poi.ss.usermodel.*
import org.modelcatalogue.builder.api.CatalogueBuilder

class ExcelLoader {

    final CatalogueBuilder builder

    public ExcelLoader(CatalogueBuilder builder) {
        this.builder = builder
    }


    def static getRowData(Row row) {
        def data = []
        for (Cell cell : row) {
            getValue(cell, data)
        }
        data
    }

    static getValue(Cell cell, List data) {
        def colIndex = cell.getColumnIndex()
        data[colIndex] = valueHelper(cell)
        data
    }

    static valueHelper(Cell cell){
        switch (cell.getCellType()) {
            case Cell.CELL_TYPE_STRING:
                return cell.getRichStringCellValue().getString().trim();
            case Cell.CELL_TYPE_NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                return cell.getNumericCellValue();
            case Cell.CELL_TYPE_BOOLEAN:
                return cell.getBooleanCellValue();
            case Cell.CELL_TYPE_FORMULA:
                return cell.getCellFormula();
        }
        return ""
    }
    private static final QUOTED_CHARS = ["\\": "&#92;", ":" : "&#58;", "|" : "&#124;", "%" : "&#37;"]

    void importData(HeadersMap headersMap, InputStream stream) {
        Workbook wb = WorkbookFactory.create(stream);
        if(!wb) {
            throw new IllegalArgumentException("Excel file contains no worksheet!")
        }
        Sheet sheet = wb.getSheetAt(0);

        Iterator<Row> rowIt = sheet.rowIterator()
        Row row = rowIt.next()
        def headers = getRowData(row)

        def rows = []
        while(rowIt.hasNext()) {
            row = rowIt.next()
            def data =getRowData(row)

            def canBeInserted = false;
            data.eachWithIndex { def entry, int i ->
                if(entry!=null && entry!="")
                    canBeInserted= true;
            }
            if(canBeInserted)
                rows << data
        }

        //get indexes of the appropriate sections
        def dataItemNameIndex = headers.indexOf(headersMap.dataElementName)
        def dataItemCodeIndex = headers.indexOf(headersMap.dataElementCode)
        def dataItemDescriptionIndex = headers.indexOf(headersMap.dataElementDescription)
        def parentModelIndex = headers.indexOf(headersMap.parentModelName)
        def modelIndex = headers.indexOf(headersMap.containingModelName)
        def parentModelCodeIndex = headers.indexOf(headersMap.parentModelCode)
        def modelCodeIndex = headers.indexOf(headersMap.containingModelCode)
        def unitsIndex = headers.indexOf(headersMap.measurementUnitName)
        def symbolsIndex = headers.indexOf(headersMap.measurementSymbol)
        def classificationsIndex = headers.indexOf(headersMap.classification)
        def dataTypeNameIndex = headers.indexOf(headersMap.dataTypeName)
        def dataTypeClassificationIndex = headers.indexOf(headersMap.dataTypeClassification)
        def dataTypeCodeIndex = headers.indexOf(headersMap.dataTypeCode)
        def valueDomainNameIndex = headers.indexOf(headersMap.valueDomainName)
        def valueDomainClassificationIndex = headers.indexOf(headersMap.valueDomainClassification)
        def valueDomainCodeIndex = headers.indexOf(headersMap.valueDomainCode)
        def metadataStartIndex = headers.indexOf(headersMap.metadata) + 1
        def metadataEndIndex = headers.size() - 1

        if (dataItemNameIndex == -1) throw new Exception("Can not find '${headersMap.dataElementName}' column")
        //iterate through the rows and import each line
        builder.build {
            copy relationships
            rows.eachWithIndex { def aRow, int i ->
                classification(name: getRowValue(aRow,classificationsIndex)) {
                    globalSearchFor dataType

                    def createChildModel = {
                        def createDataElement = {
                            if(getRowValue(aRow,dataItemNameIndex)) {
                                dataElement(name: getRowValue(aRow, dataItemNameIndex), description: getRowValue(aRow, dataItemDescriptionIndex), id: getRowValue(aRow, dataItemCodeIndex)) {
                                    if (getRowValue(aRow, unitsIndex) || getRowValue(aRow, dataTypeNameIndex)) {
                                        def createDataTypeAndMeasurementUnits = {
                                            if (getRowValue(aRow, unitsIndex))
                                                measurementUnit(name: getRowValue(aRow, unitsIndex), symbol: getRowValue(aRow, symbolsIndex))
                                            if (getRowValue(aRow, dataTypeNameIndex))
                                                importDataTypes(builder, getRowValue(aRow, dataItemNameIndex), getRowValue(aRow, dataTypeNameIndex), getRowValue(aRow, dataTypeCodeIndex), getRowValue(aRow, dataTypeClassificationIndex))
                                        }
                                        def valueDomainName = getRowValue(aRow, valueDomainNameIndex)
                                        def valueDomainCode = getRowValue(aRow, valueDomainCodeIndex)
                                        def valueDomainClassification = getRowValue(aRow, valueDomainClassificationIndex)

                                        if (!(valueDomainNameIndex || valueDomainCode || valueDomainClassification)) {
                                            valueDomain(name: getRowValue(aRow, dataItemNameIndex), classification: getRowValue(aRow, dataTypeClassificationIndex), createDataTypeAndMeasurementUnits)
                                        } else {
                                            valueDomain(name: valueDomainName, id: valueDomainCode, classification: valueDomainClassification, createDataTypeAndMeasurementUnits)
                                        }
                                    }

                                    int counter = metadataStartIndex
                                    while (counter <= metadataEndIndex) {
                                        String key = headers[counter].toString()
                                        String value = (aRow[counter] != null) ? aRow[counter].toString() : ""
                                        if (key != "" && key != "null") {
                                            ext(key, value?.take(2000)?.toString() ?: '')
                                        }
                                        counter++
                                    }
                                }
                            }
                        }


                        def modelName = getRowValue(aRow, modelIndex)
                        def modelId = getRowValue(aRow, modelCodeIndex)

                        if (modelName || modelId) {
                            model(name: modelName, id: modelId, createDataElement)
                        } else {
                            builder.with createDataElement
                        }
                    }

                    def parentModelName = getRowValue(aRow, parentModelIndex)
                    def parentModelCode = getRowValue(aRow, parentModelCodeIndex)
                    if (parentModelName || parentModelCode) {
                        model(name: parentModelName, id: parentModelCode, createChildModel)
                    } else {
                        builder.with createChildModel
                    }

                }
            }
        }
    }

    def static getRowValue(row, index){
        (index!=-1)?row[index]:null
    }


    /**
     *
     * @param dataElementName data element/item name
     * @param dataTypeNameOrEnum - Column F - content of - either blank or an enumeration or a named datatype.
     * @return
     */
    static importDataTypes(CatalogueBuilder catalogueBuilder, dataElementName, dataTypeNameOrEnum, dataTypeCode, dataTypeClassification) {
        if (!dataTypeNameOrEnum) {
            return catalogueBuilder.dataType(id: dataTypeCode, classification: dataTypeClassification, name: 'String')
        }
        //default data type to return is the string data type
        String[] lines = dataTypeNameOrEnum.split("\\r?\\n");
        if (!(lines.size() > 0 && lines != null)) {
            return catalogueBuilder.dataType(name: "String", classification: dataTypeClassification, id: dataTypeCode)
        }

        def enumerations = lines.size() == 1 ? [:] : parseEnumeration(lines)

        if(!enumerations){
            return catalogueBuilder.dataType(name: dataTypeNameOrEnum, classification: dataTypeClassification, id: dataTypeCode)
        }

        return catalogueBuilder.dataType(name: dataElementName, enumerations: enumerations, classification: dataTypeClassification, id: dataTypeCode)
    }

    static Map<String,String> parseEnumeration(String[] lines){
        Map enumerations = new HashMap()

        lines.each { enumeratedValues ->

            def EV = enumeratedValues.split(":")

            if (EV != null && EV.size() > 1 && EV[0] != null && EV[1] != null) {
                def key = EV[0]
                def value = EV[1]

                if (value.size() > 244) {
                    value = value[0..244]
                }

                key = key.trim()
                value = value.trim()


                enumerations.put(key, value)
            }
        }
        return enumerations
    }


    protected static String quote(String s) {
        if (s == null) return null
        String ret = s
        QUOTED_CHARS.each { original, replacement ->
            ret = ret.replace(original, replacement)
        }
        ret
    }
}