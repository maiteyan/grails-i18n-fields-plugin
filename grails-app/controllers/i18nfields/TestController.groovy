package i18nfields

class TestController {
	def index = {
		def name = chuchu.setName('Nombre', new Locale('fr'))
	}
}
