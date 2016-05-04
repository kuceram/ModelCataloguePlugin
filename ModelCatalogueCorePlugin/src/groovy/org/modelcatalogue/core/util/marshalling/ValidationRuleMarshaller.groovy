package org.modelcatalogue.core.util.marshalling

import org.modelcatalogue.core.ValidationRule

class ValidationRuleMarshaller extends CatalogueElementMarshaller {

    ValidationRuleMarshaller() {
        super(ValidationRule)
    }

    protected Map<String, Object> prepareJsonMap(el) {
        if (!el) return [:]
        def ret = super.prepareJsonMap(el)
        ret.putAll(
            component: el.component,
            ruleFocus: el.ruleFocus,
            trigger: el.trigger,
            rule: el.rule,
            errorCondition: el.errorCondition,
            issueRecord: el.issueRecord,
            notification: el.notification,
            notificationTarget: el.notificationTarget
        )

        ret
    }

}




