from __future__ import annotations

import os
import secrets
from fastapi import Header
from .models import UserRole


def _bearer(authorization: str | None) -> str:
    if not authorization:
        return ""
    prefix = "bearer "
    if authorization.lower().startswith(prefix):
        return authorization[len(prefix):].strip()
    return ""


def get_role(
    authorization: str | None = Header(default=None),
    x_user_role: str | None = Header(default=None),
) -> UserRole:
    token = _bearer(authorization)
    owner = os.getenv("OWNER_TOKEN", "").strip()
    premium = os.getenv("PREMIUM_TOKEN", "").strip()

    if owner and token and secrets.compare_digest(token, owner):
        return UserRole.OWNER
    if premium and token and secrets.compare_digest(token, premium):
        return UserRole.PREMIUM

    # Developer convenience only. Never enable this in production.
    if os.getenv("APP_ENV", "development").lower() == "development" and x_user_role:
        try:
            return UserRole(x_user_role.upper())
        except ValueError:
            pass

    return UserRole.FREE


def require_owner(role: UserRole) -> None:
    from fastapi import HTTPException
    if role != UserRole.OWNER:
        raise HTTPException(status_code=403, detail="OWNER access required")


def require_premium(role: UserRole) -> None:
    from fastapi import HTTPException
    if role not in {UserRole.OWNER, UserRole.PREMIUM}:
        raise HTTPException(status_code=403, detail="PREMIUM access required")
