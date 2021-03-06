package org.modelcatalogue.core.publishing.changelog

import static org.modelcatalogue.core.util.test.FileOpener.open

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.modelcatalogue.core.AbstractIntegrationSpec
import org.modelcatalogue.core.ClassificationService
import org.modelcatalogue.core.ElementService
import org.modelcatalogue.core.Model
import org.modelcatalogue.core.ModelService
import org.modelcatalogue.core.ValueDomain
import org.modelcatalogue.core.audit.AuditService
import org.modelcatalogue.core.ddl.DataDefinitionLanguage
import org.modelcatalogue.core.publishing.DraftContext
import org.modelcatalogue.core.util.builder.DefaultCatalogueBuilder

class ChangelogGeneratorSpec extends AbstractIntegrationSpec {

    AuditService auditService
    ModelService modelService
    ClassificationService classificationService
    ElementService elementService

    @Rule TemporaryFolder tmp

    def setup() {
        initCatalogueService.initDefaultRelationshipTypes()
    }

    def "test changelog export"() {
        Model draft = buildTestModel()

        when:
        File file = tmp.newFile('changelog.docx')

        ChangelogGenerator generator = new ChangelogGenerator(auditService, modelService)

        generator.generateChangelog(draft, file.newOutputStream())

        open(file)

        then:
        noExceptionThrown()
    }

    private Model buildTestModel() {
        DefaultCatalogueBuilder builder = new DefaultCatalogueBuilder(classificationService, elementService)

        Random random = new Random()


        builder.build {
            classification(name: 'C4C') {
                description "This is a classification for testing ClassificationToDocxExporter"

                ext 'foo', 'bar'
                ext 'one', '1'

                model name: 'Root Model', {
                    for (int i in 1..3) {
                        model name: "Model $i", {
                            description "This is a description for Model $i"
                            ext 'foo', 'bar'
                            ext 'boo', 'cow'

                            for (int j in 1..3) {
                                dataElement name: "Model $i Data Element $j", {

                                    valueDomain name: "Test Value Domain ${j}", {
                                        dataType name: "Test Value Domain ${i} Data Type", enumerations: (1..(i * j)).collectEntries { ["$it", "value of $it"] }
                                    }
                                    relationship {
                                        ext 'Min Occurs': '0', 'Max Occurs': "$j"
                                    }
                                }
                            }
                            for (int j in 1..3) {
                                model name: "Model $i Child Model $j", {
                                    description "This is a description for Model $i Child Model $j"

                                    for (int k in 1..3) {
                                        dataElement name: "Model $i Child Model $j Data Element $k", {
                                            description "This is a description for Model $i Child Model $j Data Element $k"
                                            valueDomain name: "Test Value Domain ${k}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return makeChanges(elementService.finalizeElement(Model.findByName('Root Model')))

    }

    private Model makeChanges(Model finalized) {
        Model model = elementService.createDraftVersion(finalized, DraftContext.userFriendly())

        // update description of C4C to
        DataDefinitionLanguage.with('C4C') {
            update 'description' of 'C4C' to 'This is a classification for testing ClassificationToDocxExporter. And now it has been changed.'
            update 'foo' of 'C4C' to 'baz'
            update 'boo' of 'C4C' to 'dar'
            update 'one' of 'C4C' to null

            create Model called 'Model XYZ', description: 'This is Model XYZ'
            update 'containment' of 'Model XYZ' add 'Model 1 Data Element 2', 'Min Occurs': 0, 'Max Occurs': 2
            update 'containment' of 'Model XYZ' add 'Model 1 Data Element 3', 'Min Occurs': 0, 'Max Occurs': 3
            update 'containment' of 'Model XYZ' add 'Model 2 Child Model 3 Data Element 1', Name: 'M2CH3DE1'

            create draft of 'Model 1 Child Model 2'
            update 'hierarchy' of 'Model 1 Child Model 2' add 'Model XYZ'
            update 'base' of  'Model 1 Child Model 1' add 'Model 1 Child Model 2' // 'Model 1 Child Model 1' is base for 'Model 1 Child Model 2'

            create draft of 'Model 1'
            update 'hierarchy' of 'Model 1' remove 'Model 1 Child Model 1'
            update 'containment' of 'Model 1' remove 'Model 1 Data Element 2'

            update 'description' of 'Model 1 Child Model 2 Data Element 1' to 'This is a description for Model 1 Child Model 2 Data Element 1 And now it has been changed.'

            create ValueDomain called 'New Value Domain'

            create draft of 'Model 1 Child Model 2 Data Element 1'
            update 'valueDomain' of 'Model 1 Child Model 2 Data Element 1' to 'New Value Domain'
            update 'dataType' of 'Test Value Domain 1' to 'Test Value Domain 2 Data Type'
            update 'enumerations' of 'Test Value Domain 3 Data Type' to one: 'jedna', eight: 'osm'

            finalize 'C4C'

        }

        return model

    }

}
