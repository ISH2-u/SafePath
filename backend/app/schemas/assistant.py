from pydantic import BaseModel, Field
from typing import List, Optional


class ChatMessageRequest(BaseModel):
    message: str = Field(..., min_length=1, max_length=1000, description="User's query to the safety assistant")
    session_id: Optional[str] = Field(None, max_length=64, description="Optional conversation session ID")


class ChatSourceCitation(BaseModel):
    topic: str
    title: str
    source_type: str


class ChatMessageResponse(BaseModel):
    reply: str
    citations: List[ChatSourceCitation] = []
    suggested_prompts: List[str] = []
    disclaimer: str = (
        "Educational safety guidance only. The assistant cannot trigger/cancel emergency alerts, "
        "cannot alter your emergency contacts, and does not replace official emergency services."
    )

