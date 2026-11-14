from fastapi import APIRouter, Depends, status
from app.models.models import User
from app.schemas.assistant import ChatMessageRequest, ChatMessageResponse, ChatSourceCitation
from app.security.deps import get_current_active_user
from app.services.safety_rag import safety_rag_service

router = APIRouter()


@router.post(
    "/chat",
    response_model=ChatMessageResponse,
    status_code=status.HTTP_200_OK,
    summary="Query the SafePath AI safety knowledge assistant",
    description=(
        "Retrieves verified safety guidance, app usage help, risk methodology, "
        "and travel practices using RAG. Strictly isolated from the SOS pipeline."
    ),
)
def chat_with_safety_assistant(
    request: ChatMessageRequest,
    current_user: User = Depends(get_current_active_user),
) -> ChatMessageResponse:
    result = safety_rag_service.answer_query(request.message)

    citations = [
        ChatSourceCitation(
            topic=c["topic"],
            title=c["title"],
            source_type=c["source_type"],
        )
        for c in result.get("citations", [])
    ]

    return ChatMessageResponse(
        reply=result["reply"],
        citations=citations,
        suggested_prompts=result.get("suggested_prompts", []),
    )

