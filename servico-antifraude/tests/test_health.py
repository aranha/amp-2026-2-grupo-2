from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from adapters.http_api import app


@pytest.mark.parametrize(("vivo", "codigo", "status"), [(True, 200, "UP"), (False, 503, "DOWN")])
def test_health_reflete_o_consumo(vivo: bool, codigo: int, status: str) -> None:
    app.state.consumo = SimpleNamespace(is_alive=lambda: vivo)

    response = TestClient(app).get("/health")

    assert response.status_code == codigo
    assert response.json() == {"status": status}
