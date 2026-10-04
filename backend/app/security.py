from fastapi import Header, HTTPException
from .models import UserRole


def get_role(x_user_role: str = Header(default="FREE")) -> UserRole:
    try:
        return UserRole(x_user_role.upper())
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="Invalid X-User-Role") from exc


def require_owner(role: UserRole) -> None:
    if role != UserRole.OWNER:
        raise HTTPException(status_code=403, detail="OWNER access required")


def require_premium(role: UserRole) -> None:
    if role not in {UserRole.OWNER, UserRole.PREMIUM}:
        raise HTTPException(status_code=403, detail="PREMIUM access required")
