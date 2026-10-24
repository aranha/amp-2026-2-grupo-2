from __future__ import annotations

import json
import urllib.error
import urllib.request
from decimal import Decimal
from uuid import UUID

from application.ports import CoreBankUnavailable


class InProcessCoreBank:
    """Deterministic local core used by tests and the local demonstration."""

    def __init__(self, failures_before_success: int = 0) -> None:
        if failures_before_success < 0:
            raise ValueError("failures_before_success must not be negative")
        self._failures_before_success = failures_before_success
        self.calls = 0

    def release_credit(
        self, solicitation_id: UUID, amount: Decimal, access_token: str | None = None
    ) -> None:
        if amount <= 0:
            raise ValueError("release amount must be positive")
        self.calls += 1
        if self.calls <= self._failures_before_success:
            raise CoreBankUnavailable(f"core unavailable for {solicitation_id}")


class ClientCredentialsToken:
    def __init__(self, issuer: str, client_id: str, client_secret: str) -> None:
        self.issuer = issuer.rstrip("/")
        self.client_id = client_id
        self.client_secret = client_secret

    def fetch(self) -> str:
        import urllib.parse
        import urllib.request

        body = urllib.parse.urlencode(
            {
                "grant_type": "client_credentials",
                "client_id": self.client_id,
                "client_secret": self.client_secret,
            }
        ).encode()
        token = access_token
        if token is None:
            try:
                token = self._token_provider.fetch()
            except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as error:
                raise CoreBankUnavailable("unable to obtain core bank token") from error
        request = urllib.request.Request(
            f"{self.issuer}/protocol/openid-connect/token",
            data=body,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            import json

            payload = json.load(response)
        token = payload.get("access_token")
        if not isinstance(token, str) or not token:
            raise RuntimeError("Keycloak did not return an access token")
        return token


class HttpCoreBank:
    def __init__(self, base_url: str, token_provider: ClientCredentialsToken) -> None:
        self._base_url = base_url.rstrip("/")
        self._token_provider = token_provider

    def release_credit(
        self, solicitation_id: UUID, amount: Decimal, access_token: str | None = None
    ) -> None:
        body = json.dumps(
            {"solicitacaoId": str(solicitation_id), "valorLiberado": str(amount)}
        ).encode()
        request = urllib.request.Request(
            f"{self._base_url}/liberacoes",
            data=body,
            method="POST",
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                if response.status >= 300:
                    raise CoreBankUnavailable(
                        f"core returned unexpected status {response.status}"
                    )
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as error:
            raise CoreBankUnavailable("core bank is unavailable") from error
