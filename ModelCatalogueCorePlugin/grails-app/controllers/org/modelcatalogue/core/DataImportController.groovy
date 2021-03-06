package org.modelcatalogue.core

import org.modelcatalogue.core.api.ElementStatus
import org.modelcatalogue.core.util.ClassificationFilter
import org.modelcatalogue.integration.excel.ExcelLoader
import org.modelcatalogue.integration.excel.HeadersMap
import org.modelcatalogue.core.dataarchitect.xsd.XsdLoader
import org.modelcatalogue.core.util.builder.DefaultCatalogueBuilder
import org.modelcatalogue.integration.obo.OboLoader
import org.modelcatalogue.integration.xml.CatalogueXmlLoader
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

class DataImportController  {

    def initCatalogueService
    def XSDImportService
    def umljService
    def loincImportService
    def modelCatalogueSecurityService
    def executorService
    def elementService
    def classificationService
    def assetService
    def auditService
    def letterAnnotatorService


    private static final CONTENT_TYPES = ['application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'application/octet-stream', 'application/xml', 'text/xml']
    static responseFormats = ['json']
    static allowedMethods = [upload: "POST"]

    protected static getErrors(Map params, MultipartFile file) {
        def errors = []
        if (file && !params.name) {
            params.name = file.originalFilename
        }
        if (!params?.name) errors.add("no import name")
        if (!file) {
            errors.add("no file")
        } else if (file.size <= 0) {
            errors.add("file is empty")
        }
        return errors
    }

    protected static trimString(string) {
        string.toString().replaceAll('\\[', "").replaceAll('\\]', "").trim()
        return string
    }

    def annotate() {
        if (!modelCatalogueSecurityService.hasRole('CURATOR')) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }


        if (!(request instanceof MultipartHttpServletRequest)) {
            respond "errors": [message: 'No file selected']
            return
        }

        def errors = []

        MultipartFile file = request.getFile("file")
        errors.addAll(getErrors(params, file))


        Set<Long> classifications = (params.classifications ?: '').split(',').collect{ Long.valueOf(it,10) }.toSet()

        if (!classifications) {
            errors << "no classifications"
        }

        if (errors) {
            respond("errors": errors)
            return
        }



        String letter = file.inputStream.text
        def id = assetService.storeReportAsAsset(
                name: params.name,
                originalFileName: params.name.endsWith('.html') ? params.name : "${params.name}.annotated.html",
                contentType: "text/html",
                description: "Your annotated letter will be available soon. Use Refresh action to reload the screen."
        )  { OutputStream out ->
            letterAnnotatorService.annotateLetter(classifications.collect{ Classification.get(it)}.toSet(), letter, out)
        }
        redirectToAsset(id)
    }

    def upload() {
        if (!modelCatalogueSecurityService.hasRole('CURATOR')) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }


        if (!(request instanceof MultipartHttpServletRequest)) {
            respond "errors": [message: 'No file selected']
            return
        }

        def errors = []

        String conceptualDomainName
        MultipartFile file = request.getFile("file")
        errors.addAll(getErrors(params, file))

        if (errors) {
            respond("errors": errors)
            return
        }
        conceptualDomainName = trimString(params.conceptualDomain)
        def confType = file.getContentType()
        boolean isAdmin = modelCatalogueSecurityService.hasRole('ADMIN')

        if (CONTENT_TYPES.contains(confType) && file.size > 0 && file.originalFilename.contains(".xls")) {
            def asset = storeAsset(params, file, 'application/vnd.ms-excel')
            def id = asset.id
            InputStream inputStream = file.inputStream
            HeadersMap headersMap = HeadersMap.create(request.JSON.headersMap ?: [:])
            executeInBackground(id, "Imported from Excel") {
                try {
                    DefaultCatalogueBuilder builder = new DefaultCatalogueBuilder(classificationService, elementService, isAdmin)
                    ExcelLoader parser = new ExcelLoader(builder)
                    parser.importData(headersMap, inputStream)
                    finalizeAsset(id)
                } catch (Exception e) {
                    logError(id, e)
                }
            }
            redirectToAsset(id)
            return
        }

        if (CONTENT_TYPES.contains(confType) && file.size > 0 && file.originalFilename.contains(".xml")) {
            def asset = storeAsset(params, file, 'application/xml')
            def id = asset.id
            InputStream inputStream = file.inputStream
            executeInBackground(id, "Imported from XML") {
                try {
                    CatalogueXmlLoader loader = new CatalogueXmlLoader(new DefaultCatalogueBuilder(classificationService, elementService, isAdmin))
                    loader.load(inputStream)
                    finalizeAsset(id)
                } catch (Exception e) {
                    logError(id, e)
                }
            }
            redirectToAsset(id)
            return
        }

        if (file.size > 0 && file.originalFilename.endsWith(".obo")) {
            def asset = storeAsset(params, file, 'text/obo')
            def id = asset.id
            InputStream inputStream = file.inputStream
            String name = params?.name
            String idpattern = params.idpattern
            executeInBackground(id, "Imported from OBO") {
                try {
                    DefaultCatalogueBuilder builder = new DefaultCatalogueBuilder(classificationService, elementService, isAdmin)
                    OboLoader loader = new OboLoader(builder)
                    idpattern = idpattern ?: "${grailsApplication.config.grails.serverURL}/catalogue/ext/${OboLoader.OBO_ID}/:id".toString().replace(':id', '$id')
                    loader.load(inputStream, name, idpattern)
                    Classification classification = builder.created.find { it.instanceOf(Classification) } as Classification
                    Asset updated = finalizeAsset(id)
                    classifyAsset(updated, classification)
                } catch (Exception e) {
                    logError(id, e)
                }
            }
            redirectToAsset(id)

            return
        }

        if (file.size > 0 && file.originalFilename.endsWith("c.csv")) {
            def asset = storeAsset(params, file, 'application/model-catalogue')
            def id = asset.id
            InputStream inputStream = file.inputStream

            executeInBackground(id, "Imported from LOINC")  {
                try {
                    Set<CatalogueElement> created = loincImportService.serviceMethod(inputStream)
                    Asset updated = finalizeAsset(id)
                    Classification classification = created.find { it instanceof Classification } as Classification
                    classifyAsset(updated, classification)
                } catch (Exception e) {
                    logError(id, e)
                }
            }

            redirectToAsset(id)
            return
        }

        if (file.size > 0 && file.originalFilename.endsWith(".mc")) {
            def asset = storeAsset(params, file, 'application/model-catalogue')
            def id = asset.id
            InputStream inputStream = file.inputStream

            executeInBackground(id, "Imported from Model Catalogue DSL")  {
                try {
                    Set<CatalogueElement> created = initCatalogueService.importMCFile(inputStream)
                    Asset updated = finalizeAsset(id)
                    Classification classification = created.find { it instanceof Classification } as Classification
                    classifyAsset(updated, classification)
                } catch (Exception e) {
                    logError(id, e)
                }
            }

            redirectToAsset(id)
            return
        }

        if (file.size > 0 && file.originalFilename.endsWith(".umlj")) {
            def asset = storeAsset(params, file, 'text/umlj')
            def id = asset.id
            InputStream inputStream = file.inputStream
            String name = params?.name

            executeInBackground(id, "Imported from Style UML")  {
                try {
                    Classification classification = Classification.findByName(name)
                    if(!classification) classification =  new Classification(name: name).save(flush:true, failOnError:true)
                    umljService.importUmlDiagram(inputStream, name, classification)
                    Asset updated = Asset.get(id)
                    updated.status = ElementStatus.FINALIZED
                    updated.description = "Your import has finished."
                    updated.save(flush: true, failOnError: true)
                    updated.addToClassifications(classification, skipUniqueChecking: true)
                    classification.addToClassifies(updated, skipUniqueChecking: true)
                } catch (Exception e) {
                    Asset updated = Asset.get(id)
                    updated.refresh()
                    updated.status = ElementStatus.FINALIZED
                    updated.name = updated.name + " - Error during upload"
                    updated.description = "Error importing umlj file: ${e}"
                    updated.save(flush: true, failOnError: true)
                }
            }

            redirectToAsset(id)
            return
        }


        if (CONTENT_TYPES.contains(confType) && file.size > 0 && file.originalFilename.contains(".xsd")) {
            Asset asset = renderImportAsAsset(params, file, conceptualDomainName)
            redirectToAsset(asset.id)
            return
        }

        if (!CONTENT_TYPES.contains(confType)) errors.add("input should be an Excel file but uploaded content is ${confType}")
        respond "errors": errors
    }

    protected static classifyAsset(Asset asset, Classification classification){
        if (classification) {
            asset.addToClassifications(classification, skipUniqueChecking: true)
        }
    }

    protected static Asset finalizeAsset(Long id){
        Asset updated = Asset.get(id)
        updated.status = ElementStatus.FINALIZED
        updated.description = "Your import has finished."
        updated.save(flush: true, failOnError: true)
        updated
    }
    protected redirectToAsset(Long id){
        response.setHeader("X-Asset-ID",  id.toString())
        redirect url: grailsApplication.config.grails.serverURL +  "/api/modelCatalogue/core/asset/" + id
    }

    protected logError(Long id,Exception e){
        log.error "Error importing Asset[$id]", e
        Asset updated = Asset.get(id)
        updated.refresh()
        updated.status = ElementStatus.FINALIZED
        updated.name = updated.name + " - Error during upload"
        updated.description = "Error importing file: ${e}"
        updated.save(flush: true, failOnError: true)
    }
    protected storeAsset(param, file, contentType = 'application/xslt'){

        String theName = (param.name ?: param.action)

        Asset asset = new Asset(
                name: "Import for " + theName,
                originalFileName: file.originalFilename,
                description: "Your import will be available in this asset soon. Use Refresh action to reload.",
                status: ElementStatus.PENDING,
                contentType: contentType,
                size: 0
        )
        asset.save(flush: true, failOnError: true)
        assetService.storeAssetFromFile(file, asset)
        return asset
    }

    protected renderImportAsAsset(param, file, conceptualDomainName){

        String uri = request.forwardURI + '?' + request.queryString
        InputStream inputStream = file.inputStream
        def asset = storeAsset(param, file)
        Long id = asset.id
        Boolean createModelsForElements = params.boolean('createModelsForElements')

        executeInBackground(id, "Rendered Import as Asset") {
            Asset updated = Asset.get(id)
            try {
                XsdLoader parserXSD = new XsdLoader(inputStream)
                def (topLevelElements, simpleDataTypes, complexDataTypes, schema, namespaces) = parserXSD.parse()
                XSDImportService.createAll(simpleDataTypes, complexDataTypes, topLevelElements, conceptualDomainName, conceptualDomainName, schema, namespaces, createModelsForElements)
                updated.status = ElementStatus.FINALIZED
                updated.description = "Your export is ready. Use Download button to view it."
                updated.ext['Original URL'] = uri
                updated.save(flush: true, failOnError: true)
            } catch (e) {
                log.error("Error importing schema", e)
                updated.refresh()
                updated.status = ElementStatus.FINALIZED
                updated.name = updated.name + " - Error during upload"
                updated.description = "Error importing file: please validate that the schema is valid xml and that any dependencies already exist in the catalogue"
                updated.save(flush: true, failOnError: true)
            }
        }
        asset
    }

    protected executeInBackground(Long assetId, String message, Closure code) {
        Long userId = modelCatalogueSecurityService.currentUser?.id
        executorService.submit {
            auditService.logExternalChange(Asset.get(assetId), userId, message, code)
        }
    }
}
