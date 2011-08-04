package i18nfields

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapExpression

import org.codehaus.groovy.ast.FieldNode
import static org.springframework.asm.Opcodes.ACC_PUBLIC
import static org.springframework.asm.Opcodes.ACC_STATIC
import java.lang.reflect.Modifier
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.ast.PropertyNode

class ClassI18nalizator {
	def classNode
	def locales

	ClassI18nalizator(ClassNode classNode, Collection<Locale> locales) {
		this.classNode = classNode
		this.locales = locales
	}

	void transformClass() {
		addLocalesMap()
		i18nFieldList.each { fieldName ->
			removeField(fieldName)
			makeFieldTransient(fieldName)
			addI18nFields(fieldName)
			addGettersAndSetters(fieldName)
		}
	}

	private addLocalesMap() {
		def i18nFields = new MapExpression()
		def localeTree = [:]
		locales.each { locale ->
			if (!localeTree.containsKey(locale.language))
				localeTree.put(locale.language, new ListExpression())
			if ("" != locale.country)
				localeTree.get(locale.language).addExpression(new ConstantExpression(locale.country))
		}
		localeTree.each {
			i18nFields.addMapEntryExpression(new ConstantExpression(it.key), it.value)
		}
		addStaticField(I18nFields.LOCALES, i18nFields)
	}

	private addStaticField(name, initialExpression) {
		def field = new FieldNode(name, ACC_PUBLIC | ACC_STATIC, new ClassNode(Object.class), classNode, initialExpression)
		// TODO: Use log4j
		println "[i18nFields] Adding ${name} static field to ${classNode.name}"
		field.setDeclaringClass(classNode)
		classNode.addField(field)
	}

	private getI18nFieldList() {
		def configuredI18nFields = classNode.getField(I18nFields.I18N_FIELDS).getInitialValueExpression().expressions.collect { it.getText() }
		def i18nFields = configuredI18nFields.findAll { fieldExists(it) }
		def invalidI18nFields = configuredI18nFields - i18nFields
		logInvalidI18nFields(invalidI18nFields)
		return i18nFields
	}

	private logInvalidI18nFields(invalidI18nFields) {
		// TODO: Use log4j
		if (invalidI18nFields.size() > 0)
			println "[i18nFields] Ignoring ${invalidI18nFields} non existant field(s)"
	}

	private removeField(name) {
		// TODO: Use log4j
		println "[i18nFields] Removing field '${name}' from class ${classNode.name}"
		classNode.properties.remove(classNode.getProperty(name))
		classNode.removeField(name)
	}

	private makeFieldTransient(name) {
		def transients = getOrCreateTransientsField().getInitialExpression()
		// TODO: Use log4j
		println "[i18nFields] Making '${name}' field of class ${classNode.name} transient"
		transients.addExpression(new ConstantExpression(name))
	}

	private getOrCreateTransientsField() {
		if (!fieldExists(I18nFields.TRANSIENTS))
			addStaticField(I18nFields.TRANSIENTS, new ListExpression())
		return classNode.getDeclaredField(I18nFields.TRANSIENTS)
	}

	private addI18nFields(baseName) {
		locales.each { locale ->
			def fieldName = "${baseName}_${locale}"
			addI18nField(fieldName)
			if (!hasConstraints(fieldName) && hasConstraints(baseName))
				copyConstraints(baseName, fieldName)
		}
	}

	private addI18nField(name) {
		// TODO: Use log4j
		println "[i18nFields] Adding '${name}' field to ${classNode.name}"
		classNode.addProperty(name, Modifier.PUBLIC, new ClassNode(String.class), new ConstantExpression(null), null, null)
	}

	private boolean hasConstraints(field) {
		return hasConstraints() && null != getConstraints(field)
	}

	private boolean hasConstraints() {
		return fieldExists(I18nFields.CONSTRAINTS)
	}

	private boolean fieldExists(name) {
		return null != classNode.getDeclaredField(name)
	}

	private getConstraints(field) {
		def closure = getConstraints().getInitialExpression().getCode()
		return closure.statements.find { statement ->
			containsAMethodCallExpression(statement) && field == statement.getExpression().getMethodAsString()
		}
	}

	private getConstraints() {
		return classNode.getDeclaredField(I18nFields.CONSTRAINTS)
	}

	private boolean containsAMethodCallExpression(statement) {
		statement instanceof ExpressionStatement && statement.getExpression() instanceof MethodCallExpression
	}

	private copyConstraints(from, to) {
		def baseMethodCall = getConstraints(from).getExpression()
		def methodCall = new MethodCallExpression(baseMethodCall.getObjectExpression(), to, baseMethodCall.getArguments())
		def newConstraints = new ExpressionStatement(methodCall)
		addConstraints(newConstraints)
	}

	private addConstraints(constraints) {
		def closure = getConstraints().getInitialExpression().getCode()
		closure.addStatement(constraints)
	}

	private addGettersAndSetters(field) {
		addProxyGetterAndSetter(field)
		addLocalizedGetterAndSetter(field)
	}

	private addProxyGetterAndSetter(field) {
		addProxyGetter(field)
		addProxySetter(field)
	}

	private addLocalizedGetterAndSetter(field) {
		addLocalizedGetter(field)
		addLocalizedSetter(field)
	}

	private addProxyGetter(field) {
		def methodName = GrailsClassUtils.getGetterName(field)
		def code = proxyGetterCode(field)
		def parameters = [] as Parameter[]
		// TODO: Use log4j
		println "[i18nFields] Adding '${methodName}()' proxy method to ${classNode.name}"
		def method = getNewMethod(methodName, parameters, code)
		classNode.addMethod(method)
	}

	private addProxySetter(field) {
		def methodName = GrailsClassUtils.getSetterName(field)
		def code = proxySetterCode(field)
		def parameters = [new Parameter(ClassHelper.make(String, false), "value")] as Parameter[]
		// TODO: Use log4j
		println "[i18nFields] Adding '${methodName}(String value)' proxy method to ${classNode.name}"
		def method = getNewMethod(methodName, parameters, code)
		classNode.addMethod(method)
	}

	private addLocalizedGetter(field) {
		def methodName = GrailsClassUtils.getGetterName(field)
		def code = localizedGetterCode(field)
		def parameters = [new Parameter(ClassHelper.make(Locale, false), "locale")] as Parameter[]
		// TODO: Use log4j
		println "[i18nFields] Adding '${methodName}(Locale locale)' helper method to ${classNode.name}"
		def method = getNewMethod(methodName, parameters, code)
		classNode.addMethod(method)
	}

	private addLocalizedSetter(field) {
		def methodName = GrailsClassUtils.getSetterName(field)
		def code = localizedSetterCode(field)
		Parameter[] parameters = [
				new Parameter(ClassHelper.make(String, false), "value"),
				new Parameter(ClassHelper.make(Locale, false), "locale")
		] as Parameter[]
		// TODO: Use log4j
		println "[i18nFields] Adding '${methodName}(String value, Locale locale)' helper method to ${classNode.name}"
		def method = getNewMethod(methodName, parameters, code)
		classNode.addMethod(method)
	}

	private proxyGetterCode = { field ->
		"""
def locale = org.springframework.context.i18n.LocaleContextHolder.getLocale()
if (${I18nFields.LOCALES}.containsKey(locale.language) && !${I18nFields.LOCALES}[locale.language].contains(locale.country))
	locale = new Locale(locale.language)
return this.\"${field}_\${locale}\"
"""
	}

	private proxySetterCode = { field ->
		"this.\"${field}_\${org.springframework.context.i18n.LocaleContextHolder.getLocale()}\" = value"
	}

	private localizedGetterCode = { field ->
		"""
if (${I18nFields.LOCALES}.containsKey(locale.language) && !${I18nFields.LOCALES}[locale.language].contains(locale.country))
	locale = new Locale(locale.language)
return this.\"${field}_\${locale}\"
"""
	}

	private localizedSetterCode = { field ->
		"this.\"${field}_\${locale}\" = value"
	}

	private getNewMethod(name, parameters, code) {
		def blockStatement = new AstBuilder().buildFromString(code).pop()
		return new MethodNode(name, ACC_PUBLIC, ClassHelper.make(String.class, false), parameters, [] as ClassNode[], blockStatement)
	}
}
