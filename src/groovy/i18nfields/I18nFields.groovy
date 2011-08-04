package i18nfields

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import java.lang.annotation.ElementType
import java.lang.annotation.Target
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Retention

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass(["i18nfields.I18nFieldsTransformation"])
public @interface I18nFields {
	static final String I18N_FIELDS = "i18nFields"
	static final String TRANSIENTS = "transients"
	static final String CONSTRAINTS = "constraints"
	static final String LOCALES = "locales"
}

