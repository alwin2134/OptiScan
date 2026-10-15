"""Vision provider abstraction and implementations for scene description and text reading."""

import base64
import logging
from abc import ABC, abstractmethod
from typing import Optional
import httpx

from app.core.config import Settings, get_settings
from app.schemas.analysis import AnalysisResponse, StatusType, TaskType

logger = logging.getLogger("optiscan.vision_provider")

# System prompt optimized specifically for blind and low-vision accessibility
SYSTEM_INSTRUCTION_ACCESSIBILITY = (
    "You are OptiScan, an AI visual assistant assisting blind and low-vision users. "
    "Your output will be spoken aloud via text-to-speech (TTS), so keep it natural, clear, and direct.\n\n"
    "Follow these accessibility and safety guidelines strictly:\n"
    "1. DIRECT ANSWERS: If the user asked a question, answer it immediately in the first sentence.\n"
    "2. SPATIAL AWARENESS: Describe locations relative to the user's perspective "
    "(e.g., 'directly in front of you', 'to your left', 'to your right', 'at waist height', 'on the floor').\n"
    "3. HAZARDS & PATHWAYS: Prioritize immediate obstacles, trip hazards, changes in elevation (steps/curbs), "
    "doorways, and visible walking path status. Do not claim absence of visible hazards proves no hazard exists; "
    "never guarantee a path or route is safe to navigate from a single 2D image.\n"
    "4. HIGH-CONSEQUENCE CONTENT & MEDICINE: Transcribe text, medication packaging, and expiry dates accurately and verbatim. "
    "Never guess or invent dosage instructions, medical uses, or health decisions.\n"
    "5. HONEST UNCERTAINTY: Explicitly state uncertainty when an object, text, distance, or detail is blurry, "
    "partially obscured, or unidentifiable. Never invent text, numbers, brands, or distances.\n"
    "6. OBJECTIVE & CONCISE: Distinguish what is directly visible from what is inferred. Avoid unnecessary flowery "
    "descriptions of colors or aesthetic appearance when the user asks a practical question. Keep responses to 2-4 sentences for TTS.\n"
    "7. CAUTION WITH ELEVATIONS & TRAFFIC: Express caution regarding curbs, drop-offs, stairs, and traffic, as depth and motion cannot be reliably judged from a single static photograph.\n"
    "8. MULTILINGUAL & HINDI SUPPORT: When the requested language is Hindi ('hi'), respond in fluent, natural Hindi (हिंदी) in Devanagari script. "
    "CRITICAL MEDICATION SAFETY: Do NOT translate detected brand names of medicines, active pharmaceutical ingredients, numerical strengths, units (such as mg, ml, mcg, tablets), or dosage text in ways that could alter or confuse their meaning. Transcribe numbers, medication names, and product labels faithfully. When reading text, quote the verbatim text accurately without inventing or guessing uncertain characters."
)


class VisionProviderError(Exception):
    """Raised when an external vision provider fails or times out."""
    pass


class VisionProvider(ABC):
    """Abstract interface for AI vision description and OCR fallback providers."""

    @abstractmethod
    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        """Generate an accessibility-first scene description or answer a question about the image."""
        pass

    @abstractmethod
    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        """Process printed text (supporting client-side Android ML Kit text)."""
        pass

    @abstractmethod
    async def guide_task(
        self,
        image_bytes: bytes,
        goal: str,
        step_history: Optional[str] = None,
        user_response: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        """Guide the user step-by-step through a requested task with visual grounding and safety checks."""
        pass


class MockVisionProvider(VisionProvider):
    """Mock vision provider for development, unit testing, and offline hackathon demos.

    Clearly indicates simulated status and does not misrepresent mock data as actual image perception.
    Provides realistic accessibility-tailored responses suitable for TTS demonstration.
    """

    def __init__(self, settings: Optional[Settings] = None):
        self.settings = settings or get_settings()

    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        logger.info(
            "MockVisionProvider describe_image called (payload size: %d bytes, mime: %s, language: %s, question: %s)",
            len(image_bytes),
            mime_type,
            language,
            question,
        )

        is_hindi = (language or "en").lower().startswith("hi")
        if is_hindi:
            if question and question.strip():
                clean_q = question.strip()
                answer = (
                    f"[Mock AI Description] सीधा उत्तर: हाँ, आपके ठीक सामने चलने का एक साफ रास्ता है। "
                    f"(आपके प्रश्न का उत्तर: '{clean_q}')। आपके दाईं ओर कमर की ऊंचाई पर पानी की बोतल वाली एक मेज है। "
                    "फर्श पर कोई बाधा नहीं है। सामान्य सावधानी से आगे बढ़ें।"
                )
            else:
                answer = (
                    "[Mock AI Description] आपके ठीक सामने एक साफ गलियारा है और अंत में एक खुला दरवाज़ा है। "
                    "आपके दाईं ओर एक मेज है जिस पर लैपटॉप रखा है। बाईं ओर एक चिकनी दीवार है। "
                    "फर्श समतल है और कोई बाधा नहीं है।"
                )
        else:
            if question and question.strip():
                clean_q = question.strip()
                answer = (
                    f"[Mock AI Description] Direct answer: Yes, there is a clear walking path directly in front of you. "
                    f"(Answering your question: '{clean_q}'). To your right at waist height is a wooden table with a water bottle. "
                    "No immediate floor hazards detected. Proceed with normal caution."
                )
            else:
                answer = (
                    "[Mock AI Description] Directly ahead of you is a clear hallway with an open doorway at the end. "
                    "To your right at waist height is an office desk with a laptop. To your left is a smooth wall. "
                    "The floor is flat with no immediate obstacles."
                )

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.DESCRIBE_IMAGE.value,
            answer=answer,
            should_recapture=False,
            reason="Running in mock provider mode. Set VISION_PROVIDER=gemini and VISION_API_KEY for live vision.",
        )

    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        logger.info(
            "MockVisionProvider process_ocr called (payload size: %d bytes, mime: %s, language: %s, has_ocr_text: %s)",
            len(image_bytes),
            mime_type,
            language,
            ocr_text is not None,
        )

        is_hindi = (language or "en").lower().startswith("hi")
        if ocr_text and ocr_text.strip():
            clean_text = ocr_text.strip()
            if is_hindi:
                if question and question.strip():
                    answer = (
                        f"[Android ML Kit OCR] \"{clean_text}\"\n"
                        f"सीधा उत्तर '{question.strip()}': लेबल पर मिली जानकारी के अनुसार विवरण की पुष्टि की गई।"
                    )
                else:
                    answer = f"[Android ML Kit OCR Text] {clean_text}"
                reason = "Text recognized by client-side Google ML Kit OCR engine."
            else:
                if question and question.strip():
                    answer = (
                        f"[Android ML Kit OCR] \"{clean_text}\"\n"
                        f"Direct answer to '{question.strip()}': The information was found on the recognized label."
                    )
                else:
                    answer = f"[Android ML Kit OCR Text] {clean_text}"
                reason = "Text recognized by client-side Google ML Kit OCR engine."
        else:
            if is_hindi:
                answer = (
                    "[Mock OCR Fallback] ROOM 302 - ACCESSIBLE ENTRANCE. कृपया सहायता के लिए घंटी बजाएं। "
                    "(नोट: Android पर Google ML Kit प्राथमिक OCR इंजन है; सर्वर ने छवि बाइट्स पर पाठ पहचान नहीं की)।"
                )
                reason = "Google ML Kit on Android handles primary on-device text recognition."
            else:
                answer = (
                    "[Mock OCR Fallback] ROOM 302 - ACCESSIBLE ENTRANCE. PLEASE RING BELL FOR ASSISTANCE. "
                    "(Note: Google ML Kit on Android is the primary OCR engine; server-side OCR was not performed on image bytes)."
                )
                reason = "Google ML Kit on Android handles primary on-device text recognition."

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.READ_TEXT.value,
            answer=answer,
            should_recapture=False,
            reason=reason,
        )

    async def guide_task(
        self,
        image_bytes: bytes,
        goal: str,
        step_history: Optional[str] = None,
        user_response: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        logger.info(
            "MockVisionProvider guide_task called (bytes: %d, goal: %s, history: %s, user_resp: %s, lang: %s)",
            len(image_bytes),
            goal,
            step_history,
            user_response,
            language,
        )
        is_hindi = (language or "en").lower().startswith("hi")
        clean_user = (user_response or "").strip().lower()
        history_str = (step_history or "").strip().lower()

        # Handle 'stop' intent
        if "stop" in clean_user or "रोक" in clean_user:
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer="टास्क सत्र रोक दिया गया है।" if is_hindi else "Task session stopped.",
                should_recapture=False,
                is_task_complete=True,
                reason="Guided task session stopped by user.",
            )

        # Procedural step progression (e.g., tea making or general tasks)
        if not history_str and not clean_user:
            answer = (
                "[Simulated Guidance - Mock Mode] मुझे आपकी मेज पर एक कप और एक केतली दिखाई दे रही है। सुरक्षित रूप से शुरू करने के लिए: क्या केतली में पानी भरा है?"
                if is_hindi else
                "[Simulated Guidance - Mock Mode] I can see a cup and a kettle on your table. To begin safely: can you confirm if the kettle contains water?"
            )
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer=answer,
                step_index=0,
                needs_clarification=True,
                is_task_complete=False,
                reason="Initial workspace observation requiring tactile water level clarification.",
            )
        elif "step 1" not in history_str and "कदम 1" not in history_str:
            answer = (
                "[Simulated Guidance - Mock Mode] कदम 1: केतली का प्लग लगाएं और स्विच चालू करें। उबलने के बाद केतली के अपने आप बंद (क्लिक) होने की प्रतीक्षा करें। तापमान जांचने के लिए केतली को न छुएं।"
                if is_hindi else
                "[Simulated Guidance - Mock Mode] Step 1: Ensure the kettle is plugged in and switched on. Wait for it to click off automatically once boiled. Do not touch the body of the kettle to check heat."
            )
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer=answer,
                step_index=1,
                needs_clarification=False,
                is_task_complete=False,
                reason="First procedural step provided.",
            )
        elif "step 2" not in history_str and "कदम 2" not in history_str:
            answer = (
                "[Simulated Guidance - Mock Mode] कदम 2: कप में एक टी बैग रखें।"
                if is_hindi else
                "[Simulated Guidance - Mock Mode] Step 2: Place a tea bag into your cup on the table."
            )
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer=answer,
                step_index=2,
                needs_clarification=False,
                is_task_complete=False,
                reason="Second procedural step provided.",
            )
        elif "step 3" not in history_str and "कदम 3" not in history_str:
            answer = (
                "[Simulated Guidance - Mock Mode] कदम 3: सावधानी से केतली से गर्म पानी कप में तीन-चौथाई भरें। केवल हैंडल पकड़ें, गर्म तरल से सावधान रहें।"
                if is_hindi else
                "[Simulated Guidance - Mock Mode] Step 3: Carefully pour hot water from the kettle into the cup up to three quarters full. Grip only the insulated handle; exercise caution with hot liquids."
            )
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer=answer,
                step_index=3,
                needs_clarification=False,
                is_task_complete=False,
                reason="Third procedural step provided with safety caution.",
            )
        elif "step 4" not in history_str and "कदम 4" not in history_str:
            answer = (
                "[Simulated Guidance - Mock Mode] कदम 4: टी बैग को 2 मिनट के लिए भीगने दें, फिर धागा पकड़कर सावधानी से निकाल लें।"
                if is_hindi else
                "[Simulated Guidance - Mock Mode] Step 4: Allow the tea bag to steep for 2 minutes, then remove it carefully by the string."
            )
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer=answer,
                step_index=4,
                needs_clarification=False,
                is_task_complete=False,
                reason="Fourth procedural step provided.",
            )
        else:
            answer = (
                "[Simulated Guidance - Mock Mode] आपकी चाय तैयार है! सावधानी से आनंद लें।"
                if is_hindi else
                "[Simulated Guidance - Mock Mode] Your tea is ready! Enjoy your drink with care."
            )
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.GUIDED_TASK.value,
                answer=answer,
                step_index=5,
                needs_clarification=False,
                is_task_complete=True,
                reason="Guided task complete.",
            )



class GeminiVisionProvider(VisionProvider):
    """Live vision provider integration with Google Gemini multimodal REST API.

    Uses official Google Gemini v1beta REST endpoint with header authentication (x-goog-api-key)
    to protect credentials from query parameter log leaks.
    """

    def __init__(self, api_key: str, model_name: str = "gemini-1.5-flash", timeout_seconds: float = 15.0):
        self.api_key = api_key
        self.model_name = model_name
        self.timeout_seconds = timeout_seconds
        self.endpoint_url = (
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:generateContent"
        )
        logger.info("GeminiVisionProvider initialized with model %s", self.model_name)

    async def describe_image(
        self,
        image_bytes: bytes,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        if not image_bytes:
            raise VisionProviderError("Image data cannot be empty.")

        b64_image = base64.b64encode(image_bytes).decode("utf-8")
        is_hindi = (language or "en").lower().startswith("hi")

        if question and question.strip():
            if is_hindi:
                user_prompt = (
                    f"Question from user: '{question.strip()}'.\n"
                    "Answer this question directly first in natural, clear Hindi (हिंदी in Devanagari script), "
                    "then concisely note any immediate obstacles or walking path details. "
                    "Keep medicine brand names, numbers, and dosage units in their original form."
                )
            else:
                user_prompt = (
                    f"Question from user: '{question.strip()}'.\n"
                    "Answer this question directly first, then concisely note any immediate obstacles or walking path details."
                )
        else:
            if is_hindi:
                user_prompt = (
                    "Describe this scene concisely for a blind or low-vision user in natural, clear Hindi (हिंदी in Devanagari script). "
                    "Highlight key objects, their spatial positions relative to the user, and immediate hazards or walking path status. "
                    "Keep responses concise (2-4 sentences) for text-to-speech. Do not invent details."
                )
            else:
                user_prompt = (
                    "Describe this scene concisely for a blind or low-vision user. "
                    "Highlight key objects, their spatial positions relative to the user, and immediate hazards or walking path status."
                )

        parts = [
            {"text": user_prompt},
            {
                "inline_data": {
                    "mime_type": mime_type,
                    "data": b64_image,
                }
            },
        ]
        answer = await self._post_gemini_parts(parts)

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.DESCRIBE_IMAGE.value,
            answer=answer,
            should_recapture=False,
            reason=None,
        )

    async def _post_gemini_parts(self, parts: list[dict]) -> str:
        generation_config = {
            "temperature": 0.2,
            "maxOutputTokens": 1024,
        }
        if "2.5" in self.model_name or "flash" in self.model_name:
            generation_config["thinkingConfig"] = {"thinkingBudget": 0}

        payload = {
            "system_instruction": {
                "parts": [{"text": SYSTEM_INSTRUCTION_ACCESSIBILITY}]
            },
            "contents": [
                {
                    "parts": parts,
                }
            ],
            "generationConfig": generation_config,
        }

        # Use header authentication rather than URL query parameter to avoid leaking keys in logs
        headers = {
            "x-goog-api-key": self.api_key,
            "Content-Type": "application/json",
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(
                    self.endpoint_url,
                    headers=headers,
                    json=payload,
                )

            if response.status_code != 200:
                self._handle_upstream_error(response)

            data = response.json()

            # Inspect safety block on prompt level
            prompt_feedback = data.get("promptFeedback", {})
            if prompt_feedback.get("blockReason"):
                block_reason = prompt_feedback.get("blockReason")
                logger.warning("Gemini prompt blocked: %s", block_reason)
                raise VisionProviderError(f"Image analysis was blocked by safety policy ({block_reason}).")

            candidates = data.get("candidates", [])
            if not candidates:
                raise VisionProviderError("Gemini API returned no analysis candidates.")

            first_candidate = candidates[0]
            finish_reason = first_candidate.get("finishReason", "")
            if finish_reason == "SAFETY":
                raise VisionProviderError("Image description was blocked by content safety filters.")

            content_parts = first_candidate.get("content", {}).get("parts", [])
            answer = "".join(p.get("text", "") for p in content_parts).strip()

            if not answer:
                raise VisionProviderError("Gemini API returned an empty description.")

            return answer
        except httpx.TimeoutException as e:
            logger.error("Timeout connecting to Gemini API (%s seconds)", self.timeout_seconds)
            raise VisionProviderError("Vision provider timed out while analyzing the image.") from e
        except httpx.NetworkError as e:
            logger.error("Network error connecting to Gemini API: %s", str(e))
            raise VisionProviderError("Network connection error reaching vision AI service.") from e
        except VisionProviderError:
            raise
        except Exception as e:
            logger.error("Unexpected error during Gemini API call: %s", str(e))
            raise VisionProviderError(f"Vision provider error: {str(e)}") from e

    def _handle_upstream_error(self, response: httpx.Response) -> None:
        """Parse upstream error response and raise tailored VisionProviderError without exposing keys."""
        status_code = response.status_code
        upstream_detail = ""
        try:
            err_json = response.json()
            if "error" in err_json:
                upstream_detail = err_json["error"].get("message", "")
        except Exception:
            upstream_detail = response.text[:200]

        logger.error("Gemini API error (HTTP %d): %s", status_code, upstream_detail)

        if status_code in (401, 403):
            raise VisionProviderError("Invalid or unauthorized Gemini API key. Please check your credentials.")
        elif status_code == 404:
            raise VisionProviderError(f"Configured Gemini model '{self.model_name}' was not found. Please verify VISION_MODEL_NAME.")
        elif status_code == 429:
            raise VisionProviderError("Gemini API rate limit or quota exceeded. Please try again shortly.")
        elif status_code >= 500:
            raise VisionProviderError("Gemini AI service is currently unavailable. Please try again later.")
        else:
            raise VisionProviderError(f"Gemini API returned HTTP {status_code}: {upstream_detail or 'Bad request'}")

    async def process_ocr(
        self,
        image_bytes: bytes,
        ocr_text: Optional[str] = None,
        question: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        is_hindi = (language or "en").lower().startswith("hi")

        if ocr_text and ocr_text.strip():
            clean_ocr = ocr_text.strip()
            if question and question.strip():
                clean_q = question.strip()
                if is_hindi:
                    ocr_prompt = (
                        f"उपयोगकर्ता का प्रश्न: '{clean_q}'।\n"
                        f"दस्तावेज़/छवि से पहचाना गया टेक्स्ट:\n\"\"\"{clean_ocr}\"\"\"\n\n"
                        "कृपया पहचाने गए टेक्स्ट और छवि साक्ष्य के आधार पर उपयोगकर्ता के प्रश्न का सीधा, संक्षिप्त और सटीक उत्तर प्राकृतिक हिंदी में दें। "
                        "दवा के नाम, संख्याएँ और खुराक की इकाइयों को सटीक रखें।"
                    )
                else:
                    ocr_prompt = (
                        f"User Question: '{clean_q}'\n"
                        f"Recognized text from document/image:\n\"\"\"{clean_ocr}\"\"\"\n\n"
                        "Answer the user's question directly and concisely based on the recognized text and image evidence. "
                        "Preserve exact dosages, instructions, or dates if asked."
                    )

                if image_bytes:
                    # Provide multimodal context (both OCR text and image visual evidence)
                    response = await self.describe_image(
                        image_bytes,
                        question=ocr_prompt,
                        mime_type=mime_type,
                        language=language,
                    )
                    return AnalysisResponse(
                        status=response.status,
                        task=TaskType.READ_TEXT.value,
                        answer=response.answer,
                        should_recapture=response.should_recapture,
                        reason="Answer generated by Gemini vision reasoning using OCR context.",
                    )
                else:
                    # Query Gemini using OCR text context without requiring image bytes
                    parts = [{"text": ocr_prompt}]
                    answer = await self._post_gemini_parts(parts)
                    return AnalysisResponse(
                        status=StatusType.SUCCESS.value,
                        task=TaskType.READ_TEXT.value,
                        answer=answer,
                        should_recapture=False,
                        reason="Answer generated by Gemini reasoning using OCR context.",
                    )

            # No question asked: return high-confidence client-side OCR text directly
            return AnalysisResponse(
                status=StatusType.SUCCESS.value,
                task=TaskType.READ_TEXT.value,
                answer=f"Recognized text: {clean_ocr}",
                should_recapture=False,
                reason="Text recognized by client-side Google ML Kit OCR engine.",
            )

        # Server-side vision fallback for text extraction when client OCR is absent
        if is_hindi:
            prompt = (
                f"Transcribe the printed text in this image verbatim and answer in Hindi: '{question.strip()}'. "
                "CRITICAL: Do not translate medicine brand names, numbers, or dosage units."
                if question and question.strip()
                else "Transcribe the prominent printed text in this image verbatim. Note signs, headings, or product names. "
                     "Explain concisely in natural Hindi, keeping English brand names, numbers, and dosage units intact."
            )
        else:
            prompt = (
                f"Transcribe the printed text in this image and answer: '{question.strip()}'"
                if question and question.strip()
                else "Transcribe the prominent printed text in this image verbatim. Note signs, headings, or product names."
            )
        response = await self.describe_image(image_bytes, question=prompt, mime_type=mime_type, language=language)
        return AnalysisResponse(
            status=response.status,
            task=TaskType.READ_TEXT.value,
            answer=response.answer,
            should_recapture=response.should_recapture,
            reason=response.reason,
        )

    async def guide_task(
        self,
        image_bytes: bytes,
        goal: str,
        step_history: Optional[str] = None,
        user_response: Optional[str] = None,
        mime_type: str = "image/jpeg",
        language: str = "en",
    ) -> AnalysisResponse:
        """Provide single-step, safety-conserving visual guidance grounded in workspace imagery."""
        if not image_bytes:
            raise VisionProviderError("Image data cannot be empty for visual task guidance.")

        is_hindi = (language or "en").lower().startswith("hi")
        b64_image = base64.b64encode(image_bytes).decode("utf-8")

        prompt_instructions = (
            f"GOAL: {goal.strip()}\n"
            f"PRIOR CONTEXT / COMPLETED STEPS:\n{step_history.strip() if step_history else 'None (Initial scan)'}\n"
            f"USER INPUT / CLARIFICATION:\n{user_response.strip() if user_response else 'None'}\n\n"
            "TASK INSTRUCTIONS FOR ASSISTING BLIND/LOW-VISION USER (AROMA & Be My Eyes Safety Standard):\n"
            "1. Inspect the workspace image carefully against the goal and prior context.\n"
            "2. State what relevant items are visible and their relative positions.\n"
            "3. If an essential item, container volume, temperature, or switch state cannot be visually confirmed with certainty, "
            "DO NOT GUESS. Ask the user a short clarification question or ask them to tilt/rescan.\n"
            "4. Give EXACTLY ONE next actionable, concise instruction. Do NOT list multiple steps at once.\n"
            "5. Apply conservative safety around hot liquids, burners, steam, sharp utensils, and electrical sockets. "
            "Never claim an appliance is cool or off without visual confirmation.\n"
            "6. DO NOT instruct the user to touch appliances, heating elements, or hot surfaces to check temperature. "
            "DO NOT use auditory cues alone (such as bubbling or boiling sounds) as proof of safety without visual confirmation of switch shutoff or steam.\n"
            "7. Never assume a physical action was completed merely because it appeared in prior context. Verify the current visual evidence. "
            "If the physical state cannot be confirmed from the image, request a rescan or clarification.\n"
            "8. If the overall goal is fully achieved, clearly state that the task is complete.\n"
        )
        if is_hindi:
            prompt_instructions += (
                "\nCRITICAL: Respond entirely in natural Hindi (Devanagari script). "
                "Keep numbers, units, and brand names exact."
            )

        parts = [
            {"text": prompt_instructions},
            {
                "inline_data": {
                    "mime_type": mime_type,
                    "data": b64_image,
                }
            },
        ]

        answer = await self._post_gemini_parts(parts)

        is_complete = any(
            phrase in answer.lower()
            for phrase in [
                "task complete",
                "task is complete",
                "finished",
                "all done",
                "कार्य पूरा हुआ",
                "तैयार है",
            ]
        )
        needs_clarif = "?" in answer or "confirm" in answer.lower() or "check" in answer.lower() or "क्या" in answer

        return AnalysisResponse(
            status=StatusType.SUCCESS.value,
            task=TaskType.GUIDED_TASK.value,
            answer=answer,
            should_recapture=False,
            is_task_complete=is_complete,
            needs_clarification=needs_clarif,
            reason="Guided task instruction generated with visual context.",
        )



def get_vision_provider(settings: Optional[Settings] = None) -> VisionProvider:
    """Factory creating the appropriate VisionProvider based on environment configuration.

    Raises clear configuration errors if real-provider mode is selected without credentials.
    """
    resolved_settings = settings or get_settings()
    provider_type = resolved_settings.vision_provider.lower().strip()

    if provider_type == "mock":
        return MockVisionProvider(settings=resolved_settings)

    if provider_type == "gemini":
        if not resolved_settings.vision_api_key or not resolved_settings.vision_api_key.strip():
            raise ValueError(
                "VISION_PROVIDER is set to 'gemini' but VISION_API_KEY is missing or empty. "
                "Please configure VISION_API_KEY in your .env file or set VISION_PROVIDER='mock'."
            )
        return GeminiVisionProvider(
            api_key=resolved_settings.vision_api_key.strip(),
            model_name=resolved_settings.vision_model_name,
            timeout_seconds=resolved_settings.vision_request_timeout_seconds,
        )

    if provider_type == "openai":
        if not resolved_settings.vision_api_key or not resolved_settings.vision_api_key.strip():
            raise ValueError(
                "VISION_PROVIDER is set to 'openai' but VISION_API_KEY is missing or empty. "
                "Please configure VISION_API_KEY in your .env file or set VISION_PROVIDER='mock'."
            )
        raise NotImplementedError("OpenAI provider integration is scheduled for next iteration.")

    raise ValueError(f"Unknown vision_provider: '{provider_type}'")
