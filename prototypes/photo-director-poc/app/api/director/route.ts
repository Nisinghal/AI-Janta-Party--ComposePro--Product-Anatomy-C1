import { NextResponse } from 'next/server';
import { GoogleGenAI } from '@google/genai';

const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });

export async function POST(req: Request) {
  try {
    const { image, prompt } = await req.json();

    if (!image) {
      return NextResponse.json({ error: 'Image is required' }, { status: 400 });
    }

    // Extract base64 data (remove "data:image/jpeg;base64,")
    const base64Data = image.split(',')[1] || image;

    const response = await ai.models.generateContent({
      model: 'gemini-2.5-pro',
      contents: [
        {
          role: 'user',
          parts: [
            { text: prompt || 'You are an expert photography director. Give one short, actionable piece of advice (under 15 words) on how to improve this composition, lighting, or styling.' },
            {
              inlineData: {
                data: base64Data,
                mimeType: 'image/jpeg'
              }
            }
          ]
        }
      ]
    });

    const advice = response.text || 'Looking good!';

    return NextResponse.json({ advice });
  } catch (error: any) {
    console.error('Gemini API Error:', error);
    return NextResponse.json({ error: error.message || 'Failed to process image' }, { status: 500 });
  }
}
