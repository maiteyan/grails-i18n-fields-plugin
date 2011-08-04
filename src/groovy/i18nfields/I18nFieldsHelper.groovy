package i18nfields

import org.springframework.context.i18n.LocaleContextHolder

class I18nFieldsHelper {
	static transients_model = ["fieldname"]

	static setLocale(Locale locale) {
		LocaleContextHolder.setLocale(locale)
	}

	static getLocale() {
		return LocaleContextHolder.getLocale()
	}

	static withLocale = { Locale newLocale, Closure closure ->
		def previousLocale = i18nfields.I18nFieldsHelper.getLocale()
		i18nfields.I18nFieldsHelper.setLocale(newLocale)
		def result = closure.call()
		i18nfields.I18nFieldsHelper.setLocale(previousLocale)
		return result
	}
}

