"""
Safety RAG Service — Knowledge Base & Retrieval-Augmented Generation for SafePath AI.

CRITICAL DESIGN & SAFETY PRINCIPLES:
1. Grounded only in verified personal safety practices and SafePath AI official system rules.
2. NEVER invents crime statistics or guarantees complete safety for any area.
3. CANNOT trigger or suppress emergency SOS; clearly directs user to the SOS button if requested.
4. CANNOT alter user contact records or access other users' data.
5. Strictly isolated from the real-time emergency dispatch pipeline.
"""

import math
import re
from typing import List, Dict, Any, Tuple


KNOWLEDGE_BASE = [
    {
        "id": "kb_sos_manual",
        "topic": "emergency_sos",
        "title": "Manual SOS Emergency System",
        "keywords": ["sos", "emergency", "alert", "help", "panic", "button", "trigger", "danger"],
        "content": (
            "SafePath AI provides a prominent Manual SOS button on the home screen and dedicated SOS screen. "
            "When activated, the app retrieves high-accuracy GPS coordinates (or clear last-known location if GPS is unavailable), "
            "saves the alert locally in an offline Room database to survive network failures, and dispatches an emergency message "
            "strictly to your verified emergency contacts. Alerts contain a live map location link and timestamp. "
            "The system uses idempotency keys to prevent accidental duplicate alerts."
        ),
        "source": "SafePath AI Emergency Architecture Guide",
    },
    {
        "id": "kb_voice_codeword",
        "topic": "voice_codeword",
        "title": "Personalized Voice Emergency Codeword",
        "keywords": ["voice", "codeword", "phrase", "microphone", "secret", "whisper", "handsfree", "hands-free"],
        "content": (
            "You can configure a private emergency phrase (such as 'Blue Moon') in Voice Settings. "
            "SafePath AI uses 100% on-device speech recognition to listen locally for your keyword without ever "
            "streaming raw microphone audio to the cloud. When recognized, it immediately dispatches emergency SOS "
            "without requiring secondary confirmation or extra button presses. "
            "Note: Android limits background microphone access when locked in deep sleep, so use a foreground service "
            "and always keep the manual SOS button accessible."
        ),
        "source": "SafePath AI On-Device Voice Privacy Reference",
    },
    {
        "id": "kb_contacts_management",
        "topic": "contacts",
        "title": "Emergency Contacts Management & Verification",
        "keywords": ["contact", "contacts", "verify", "verification", "add contact", "delete contact", "phone", "recipient"],
        "content": (
            "To safeguard user trust, SafePath AI requires explicit verification for all emergency contacts. "
            "Only contacts you explicitly add, verify via SMS/link, and enable for SOS alerts will receive notifications. "
            "SafePath AI strictly NEVER automatically notifies police, strangers, or public feeds. "
            "SOS alert recipients are retrieved directly from your verified contact records on the secure backend."
        ),
        "source": "SafePath AI Contact Privacy & Trust Guidelines",
    },
    {
        "id": "kb_area_risk",
        "topic": "area_risk",
        "title": "How Area-Risk Information is Estimated",
        "keywords": ["risk", "crime", "safe", "danger", "model", "statistics", "h3", "heat map", "incident", "estimate"],
        "content": (
            "SafePath AI evaluates relative area-risk using historical crime data aggregated across geographic hexagonal grids (H3). "
            "Models estimate relative recorded incident activity into Low, Moderate, or High categories. "
            "Crucially, low recorded crime does NOT guarantee safety—sparse reporting or lack of data results in an "
            "'INSUFFICIENT DATA' classification rather than a misleading 'Safe' label. "
            "SafePath AI never predicts individual victimization and estimates should be used for situational awareness only."
        ),
        "source": "SafePath AI Machine Learning Methodology",
    },
    {
        "id": "kb_journey_tracking",
        "topic": "journey_tracking",
        "title": "Live Journey Tracking & Route Safety",
        "keywords": ["journey", "track", "tracking", "route", "gps", "travel", "map", "destination", "walk"],
        "content": (
            "When starting a journey, SafePath AI uses the Google Fused Location Provider to record start time, "
            "current position, and route progress with explicit accuracy metrics. Journey tracking operates in a visible "
            "foreground service with a persistent notification. Location data is stored locally and never shared with "
            "unauthorized parties. You can stop or pause journey tracking at any time."
        ),
        "source": "SafePath AI Journey Tracking Documentation",
    },
    {
        "id": "kb_travel_safety_tips",
        "topic": "personal_safety",
        "title": "General Personal Safety & Travel Practices",
        "keywords": ["travel", "traveling", "night", "alone", "walking", "transit", "cab", "uber", "safety tips", "awareness"],
        "content": (
            "When traveling alone or at night: "
            "1. Stay in well-lit, populated corridors and avoid deserted shortcuts. "
            "2. Keep your mobile phone sufficiently charged and accessible. "
            "3. When using rideshare services, always match the license plate, driver photo, and vehicle make before entering. "
            "4. Maintain situational awareness by avoiding using noise-canceling headphones in isolated areas. "
            "5. Have your emergency contacts verified in advance and know your voice codeword."
        ),
        "source": "International Personal Safety & Urban Commute Guidelines",
    },
]


def tokenize(text: str) -> List[str]:
    return re.findall(r"\b[a-zA-Z0-9]{3,}\b", text.lower())


class SafetyRAGService:
    def __init__(self):
        self.docs = KNOWLEDGE_BASE

    def retrieve(self, query: str, top_k: int = 2) -> List[Dict[str, Any]]:
        query_tokens = set(tokenize(query))
        if not query_tokens:
            return self.docs[:top_k]

        scored_docs: List[Tuple[float, Dict[str, Any]]] = []
        for doc in self.docs:
            score = 0.0
            doc_tokens = set(tokenize(doc["title"] + " " + doc["content"]))

            # Keyword match
            for kw in doc["keywords"]:
                for qt in query_tokens:
                    if qt in kw.lower():
                        score += 3.0

            # Title overlap
            title_tokens = set(tokenize(doc["title"]))
            score += len(query_tokens.intersection(title_tokens)) * 2.5

            # Body overlap
            overlap = query_tokens.intersection(doc_tokens)
            score += len(overlap) * 1.0

            scored_docs.append((score, doc))

        scored_docs.sort(key=lambda x: x[0], reverse=True)
        return [doc for score, doc in scored_docs[:top_k] if score > 0] or self.docs[:1]

    def answer_query(self, user_query: str) -> Dict[str, Any]:
        normalized_q = user_query.strip().lower()

        # Guardrail 1: User asks assistant to trigger SOS
        if any(term in normalized_q for term in ["trigger sos", "send sos", "call 911", "call police", "i am in danger now", "help me now"]):
            return {
                "reply": (
                    "⚠️ IF YOU ARE IN IMMEDIATE DANGER: Tap the prominent red SOS BUTTON on your screen right now, "
                    "or clearly speak your configured voice emergency phrase. "
                    "As an AI safety assistant, I cannot directly trigger an emergency alert for you. "
                    "Pressing the SOS button will immediately dispatch your GPS coordinates to your verified emergency contacts."
                ),
                "citations": [
                    {
                        "topic": "emergency_sos",
                        "title": "Manual SOS Emergency System",
                        "source_type": "Official Guide",
                    }
                ],
                "suggested_prompts": [
                    "How does the SOS button work?",
                    "How to setup voice emergency phrase?",
                    "Who receives my SOS alerts?",
                ],
            }

        # Guardrail 2: User asks assistant to edit contacts
        contact_modification_pattern = re.compile(
            r"\b(add|change|delete|remove|edit|update|modify|create)\b.*?\b(contact|contacts|phone|number)\b|"
            r"\b(contact|contacts)\b.*?\b(add|change|delete|remove|edit|update|modify|create)\b",
            re.IGNORECASE,
        )
        if contact_modification_pattern.search(normalized_q):
            return {
                "reply": (
                    "For your security and privacy, I cannot view or modify your emergency contacts directly. "
                    "To add, edit, verify, or configure SOS alerts for contacts, open the 'Emergency Contacts' "
                    "tab from the bottom navigation bar. Only contacts you explicitly verify can receive emergency alerts."
                ),
                "citations": [
                    {
                        "topic": "contacts",
                        "title": "Emergency Contacts Management & Verification",
                        "source_type": "Official Guide",
                    }
                ],
                "suggested_prompts": [
                    "Why must contacts be verified?",
                    "Will police be contacted automatically?",
                    "How many contacts can I add?",
                ],
            }

        # Guardrail 3: RAG Retrieval
        retrieved_docs = self.retrieve(user_query, top_k=2)
        citations = []
        doc_summaries = []

        for d in retrieved_docs:
            citations.append({
                "topic": d["topic"],
                "title": d["title"],
                "source_type": d["source"],
            })
            doc_summaries.append(f"**{d['title']}**:\n{d['content']}")

        # Formulate grounded response
        combined_text = "\n\n".join(doc_summaries)
        reply = (
            f"Based on SafePath AI verified safety guidelines:\n\n{combined_text}\n\n"
            f"*Practical Guidance*: Keep your emergency contacts updated and verified, "
            f"and always test your personal voice codeword in safe mode."
        )

        suggested = [
            "How is area risk calculated?",
            "What should I do if walking alone at night?",
            "How does voice codeword work?",
        ]

        return {
            "reply": reply,
            "citations": citations,
            "suggested_prompts": suggested,
        }


safety_rag_service = SafetyRAGService()

