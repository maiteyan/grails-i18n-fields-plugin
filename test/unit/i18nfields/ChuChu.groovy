package i18nfields

@I18nFields
class ChuChu {
	def name
	def description
	static i18n_fields = ["name", "description"]
	static constraints = {
		name(nullable:true,min:5)
		name_en_US(min:10)
	}
}