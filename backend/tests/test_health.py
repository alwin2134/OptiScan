"""Tests for the /health status endpoint."""

from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_check_returns_ok():
    """Verify GET /health returns HTTP 200 and expected payload."""
    response = client.get("/health")
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "optiscan-backend"
