package com.bbh.itss.dso.portal.regression

import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasksJson
import static com.bbh.itss.dso.portal.support.ChangeFixtures.templateJson

class ProductRegressionSpec extends ChangeRegressionSpecification {

    def "a product is created under a suggested code, read back and found by any of its names"() {
        given:
        def name = "Ledger ${uniqueCode()}".toString()
        def code = api.get("/api/products/code-suggestion?name=${enc(name)}").json.code

        when:
        def created = api.post('/api/products', product(code: code, name: name, departmentId: 4))

        then:
        created.status == 201
        created.header('Location') == "$api.baseUrl/api/products/${created.json.id}"
        created.json == [id          : created.json.id, code: code, name: name, ownerTeam: 'Technology Architecture',
                         contactEmail: 'ta-team@bbh.com', departmentId: 4, departmentName: 'Custody', version: 0,
                         updatedAt   : created.json.updatedAt]
        api.get("/api/products/$created.json.id").json == created.json
        api.get("/api/products?search=${enc(name.toUpperCase())}").json*.id == [created.json.id]
        api.get("/api/products?search=custody").json*.departmentName.unique() == ['Custody']
    }

    def "a change keeps the code, clears blank values and is refused when based on an old version"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))

        when:
        def changed = api.put("/api/products/$created.id", product(code: 'OTHER', name: "Renamed $code",
                ownerTeam: ' ', departmentId: 5, version: created.version))
        def stale = api.put("/api/products/$created.id", product(code: code, name: "Stale $code",
                version: created.version))

        then:
        changed.status == 200
        changed.json == created + [name: "Renamed $code".toString(), ownerTeam: null, departmentId: 5,
                                   departmentName: 'Fund Services', version: 1, updatedAt: changed.json.updatedAt]
        stale.status == 409
        stale.json.detail.contains('changed by someone else')
        api.get("/api/products/$created.id").json.name == "Renamed $code"
    }

    def "a product is refused field by field"() {
        given:
        def taken = createProduct(product(code: uniqueCode(), name: "Taken ${uniqueCode()}"))

        expect:
        api.post('/api/products', product(code: 'lower', name: ' ', contactEmail: 'not an address'))
                .json.errors*.field.sort() == ['code', 'contactEmail', 'name']
        api.post('/api/products', product(code: uniqueCode(), name: 'Nowhere', departmentId: 99)).json.errors ==
                [[field: 'departmentId', message: 'department 99 does not exist']]
        api.post('/api/products', product(code: uniqueCode(), name: 'Long team', ownerTeam: 'Księgowość ' * 15))
                .json.errors == [[field: 'ownerTeam', message: 'is too long: it may take at most 200 bytes']]
        api.post('/api/products', product(code: taken.code, name: "Copy ${uniqueCode()}")).status == 409
        api.post('/api/products', product(code: uniqueCode(), name: taken.name.toUpperCase())).status == 409
        api.put('/api/products/999999', product(code: uniqueCode(), name: 'Missing', version: 0)).status == 404
        api.get('/api/products/999999').status == 404
    }

    def "a product is deleted with its change template while the changes raised for it keep its name"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))
        api.put("/api/products/$created.id/change-profile", [version: null, template: templateJson(), tasks: tasksJson()])
        def raised = raise(created)

        when:
        def deleted = api.delete("/api/products/$created.id")

        then:
        deleted.status == 204
        api.get("/api/products/$created.id").status == 404
        !(created.id in api.get('/api/change-profiles').json*.productId)
        with(api.get("/api/changes/$raised.id").json) {
            productId == null
            productName == "Product $code"
        }
        api.delete("/api/products/$created.id").status == 404
    }
}
