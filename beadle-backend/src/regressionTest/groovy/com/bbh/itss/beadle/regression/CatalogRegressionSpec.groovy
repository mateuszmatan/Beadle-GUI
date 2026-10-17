package com.bbh.itss.beadle.regression

import static com.bbh.itss.beadle.support.ChangeFixtures.tasksJson
import static com.bbh.itss.beadle.support.ChangeFixtures.templateJson

class CatalogRegressionSpec extends ChangeRegressionSpecification {

    def "a product is created under a suggested code, renamed at its version and deleted while its changes keep its name"() {
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
        api.get("/api/products?search=${enc(name.toUpperCase())}").json*.id == [created.json.id]
        api.post('/api/products', product(code: 'lower', name: ' ', contactEmail: 'not an address'))
                .json.problems*.field.sort() == ['code', 'contactEmail', 'name']

        when:
        def renamed = api.put("/api/products/$created.json.id", product(code: 'OTHER', name: "Renamed $code",
                ownerTeam: ' ', departmentId: 3, version: 0))
        def stale = api.put("/api/products/$created.json.id", product(code: code, name: "Stale $code", version: 0))

        then:
        renamed.status == 200
        renamed.json == created.json + [name: "Renamed $code".toString(), ownerTeam: null, departmentId: 3,
                                        departmentName: 'Corporate Technology', version: 1,
                                        updatedAt: renamed.json.updatedAt]
        stale.status == 409
        stale.json.detail.contains('changed by someone else')

        when:
        api.put("/api/products/$created.json.id/change-profile", [version: null, template: templateJson(),
                                                                  tasks: tasksJson()])
        def raised = raise(renamed.json as Map)
        def deleted = api.delete("/api/products/$created.json.id")

        then:
        deleted.status == 204
        api.get("/api/products/$created.json.id").status == 404
        !(created.json.id in api.get('/api/change-profiles').json*.productId)
        api.get("/api/changes/$raised.id").json.subMap('productId', 'productName') ==
                [productId: null, productName: "Renamed $code".toString()]
        api.delete("/api/products/$created.json.id").status == 404
    }

    def "a department counts its products and changes and is deleted only when empty"() {
        given:
        def department = createDepartment()
        def products = (1..2).collect {
            createProduct(product(code: uniqueCode(), name: "Product ${uniqueCode()}", departmentId: department.id))
        }
        def raised = raise(products[0])

        expect:
        [raised.departmentId, raised.departmentName] == [department.id, department.name]
        api.get('/api/departments').json.find { it.id == department.id } ==
                department + [productCount: 2, changeCount: 1]
        api.delete("/api/departments/$department.id").json.detail ==
                "$department.name still has 2 product(s). Move them to another department first."

        when:
        products.each { api.put("/api/products/$it.id", product(code: it.code, name: it.name, version: it.version)) }
        def refused = api.delete("/api/departments/$department.id")
        def empty = createDepartment()

        then:
        refused.status == 409
        refused.json.detail == "$department.name still owns 1 change(s), so it cannot be deleted."
        api.get('/api/departments').json.find { it.id == department.id } ==
                department + [productCount: 0, changeCount: 1]
        api.delete("/api/departments/$empty.id").status == 204
        api.get('/api/departments').json.every { it.id != empty.id }
    }

    private Map createDepartment() {
        def response = api.post('/api/departments', [name: "Department ${uniqueCode()}"])
        assert response.status == 201: response
        response.json as Map
    }
}
