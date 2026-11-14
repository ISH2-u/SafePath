from fastapi import APIRouter
from app.api.v1.endpoints import health, auth, contacts, risk, sos, assistant

api_router = APIRouter()
api_router.include_router(health.router, prefix="", tags=["Health"])
api_router.include_router(auth.router, prefix="/auth", tags=["Authentication & Profile"])
api_router.include_router(contacts.router, prefix="/contacts", tags=["Emergency Contacts"])
api_router.include_router(risk.router, prefix="/risk", tags=["Area Risk Estimation"])
api_router.include_router(sos.router, prefix="/sos", tags=["Manual SOS Emergency"])
api_router.include_router(assistant.router, prefix="/assistant", tags=["Safety Assistant Chatbot"])
