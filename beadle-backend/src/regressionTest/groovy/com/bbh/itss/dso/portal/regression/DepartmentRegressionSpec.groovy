package com.bbh.itss.dso.portal.regression

class DepartmentRegressionSpec extends ChangeRegressionSpecification {

    def "every department counts its products and the changes raised for them"() {
        given:
        def department = createDepartment()
        def first = createProduct(product(code: uniqueCode(), name: "First ${uniqueCode()}", departmentId: department.id))
        createProduct(product(code: uniqueCode(), name: "Second ${uniqueCode()}", departmentId: department.id))
        raise(first)

        when:
        def departments = api.get('/api/departments').json

        then:
        departments*.name.containsAll(['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services'])
        departments.every { it.keySet() as List == ['id', 'name', 'version', 'productCount', 'changeCount'] }
        departments.find { it.id == department.id } == department + [productCount: 2, changeCount: 1]
    }

    def "a department that owns a change is not deleted, even once its products moved away"() {
        given:
        def department = createDepartment()
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code", departmentId: department.id))
        def raised = raise(created)
        def moved = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                version: created.version))

        when:
        def refused = api.delete("/api/departments/$department.id")

        then:
        [raised.departmentId, raised.departmentName] == [department.id, department.name]
        moved.status == 200
        refused.status == 409
        refused.json.detail == "$department.name still owns 1 change(s), so it cannot be deleted."
        api.get('/api/departments').json.find { it.id == department.id } ==
                department + [productCount: 0, changeCount: 1]
    }

    def "a department without products or changes is deleted"() {
        given:
        def department = createDepartment()

        expect:
        api.delete("/api/departments/$department.id").status == 204
        api.get('/api/departments').json.every { it.id != department.id }
    }

    private Map createDepartment() {
        def response = api.post('/api/departments', [name: "Department ${uniqueCode()}"])
        assert response.status == 201: response
        response.json as Map
    }
}
