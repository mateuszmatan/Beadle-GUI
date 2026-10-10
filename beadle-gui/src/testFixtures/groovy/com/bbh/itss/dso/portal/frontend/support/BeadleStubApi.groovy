package com.bbh.itss.dso.portal.frontend.support

import java.util.concurrent.CopyOnWriteArrayList

import static com.bbh.itss.dso.portal.frontend.support.StubResponse.empty
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.json
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem

class BeadleStubApi extends StubApi {

    static final String SIGNED_IN_USER = 'Mateusz Matan'

    static final String SAVED_AT = '2026-10-05T10:00:00Z'

    List<Map> products
    ChangeStubs.ProTech protech

    @Override
    void install() {
        installDepartments(changeCount: 0)
        products = new CopyOnWriteArrayList<Map>(fixture('products.json') as List<Map>)
        get('/api/me') { [name: SIGNED_IN_USER] }
        installProducts()
        protech = ChangeStubs.install(this)
    }

    void installProducts() {
        get('/api/products') { RecordedRequest request ->
            def search = request.params().search?.toLowerCase()
            json(search ? products.findAll {
                [it.code, it.name, it.ownerTeam, it.departmentName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
            } : products)
        }
        get('/api/products/code-suggestion') { RecordedRequest request ->
            json([code: request.params().name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')])
        }
        get('/api/products/(\\d+)') { RecordedRequest request, List<String> ids ->
            def product = product(ids[0])
            product ? json(product) : problem(404, 'Not Found', "Product ${ids[0]} was not found")
        }
        on('POST', '/api/products') { RecordedRequest request ->
            def sent = request.json() as Map
            def created = stored(sent, (products*.id.max() as int) + 1, 0)
            products << created
            json(created, 201)
        }
        on('PUT', '/api/products/(\\d+)') { RecordedRequest request, List<String> ids ->
            def sent = request.json() as Map
            def current = product(ids[0])
            if (sent.version != current.version) {
                return problem(409, 'Conflict', 'The product was changed by someone else; reload it and try again')
            }
            def saved = stored(sent + [code: current.code], current.id, (current.version as int) + 1)
            products[products.indexOf(current)] = saved
            json(saved)
        }
        on('DELETE', '/api/products/(\\d+)') { RecordedRequest request, List<String> ids ->
            products.removeIf { it.id == ids[0] as int }
            empty()
        }
    }

    Map product(Object id) {
        products.find { it.id == id as int }
    }

    private Map stored(Map sent, int id, int version) {
        [id          : id, code: sent.code, name: sent.name, ownerTeam: sent.ownerTeam, contactEmail: sent.contactEmail,
         departmentId: sent.departmentId, departmentName: departmentName(sent.departmentId), version: version,
         updatedAt   : SAVED_AT]
    }
}
