import { FaArrowsAltH } from "react-icons/fa";
import { useState, useEffect, useCallback } from "react";
import Image from "next/image";

function parseResult(result) {
  // Split the result into lines
  const lines = result.trim().split("\n");
  if (lines.length < 2) return null;
  const startIdLine = lines.find(line => line.startsWith("START_ID:"));
  const endIdLine = lines.find(line => line.startsWith("END_ID:"));
  const startId = startIdLine ? startIdLine.replace("START_ID:", "").trim() : null;
  const endId = endIdLine ? endIdLine.replace("END_ID:", "").trim() : null;

  // Find the celebrity IDs and title IDs lines (API format uses legacy naming for compatibility)
  const celebrityIdsLine = lines.find(line => line.startsWith("ACTOR_IDS:"));
  const celebrityIds = celebrityIdsLine ? celebrityIdsLine.replace("ACTOR_IDS:", "").split(",").filter(id => id) : [];

  const titleIdsLine = lines.find(line => line.startsWith("MOVIE_IDS:"));
  const titleIds = titleIdsLine ? titleIdsLine.replace("MOVIE_IDS:", "").split(",").filter(id => id) : [];

  // First line should be the celebrities (names separated by " -> ")
  const celebrities = lines[0] ? lines[0].split(" -> ") : [];

  // Structured titles only; no legacy fallback
  const titleNamesLine = lines.find(line => line.startsWith("MOVIE_TITLES:"));
  const titles = titleNamesLine ? titleNamesLine.replace("MOVIE_TITLES:", "").split(",").filter(t => t) : [];

  // Normalize orientation using explicit endpoints if provided
  if (startId && celebrityIds.length > 0 && celebrityIds[0] !== startId) {
    celebrities.reverse();
    celebrityIds.reverse();
    titles.reverse();
    titleIds.reverse();
  }

  return { celebrities, titles, celebrityIds, titleIds };
}

export default function PathResult({ result }) {
  const [celebrityPhotos, setCelebrityPhotos] = useState({});
  const parsed = parseResult(result);

  const fetchCelebrityPhoto = useCallback(async (celebrityName, celebrityId, signal) => {
    if (celebrityPhotos[celebrityName] || !celebrityId) return;
    try {
      const url = `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'}/api/celebrity-photo?celebrityId=${encodeURIComponent(celebrityId)}&celebrityName=${encodeURIComponent(celebrityName)}`;
      const response = await fetch(url, { signal });
      if (!response.ok) return;
      const data = await response.json();
      if (data.photoUrl) {
        setCelebrityPhotos(prev => ({ ...prev, [celebrityName]: data.photoUrl }));
      }
    } catch (error) {
      if (error.name !== 'AbortError') console.error("Error fetching celebrity photo:", error);
    }
  }, [celebrityPhotos]);

  // Reset photos when result changes
  useEffect(() => {
    setCelebrityPhotos({});
  }, [result]);

  // Fetch photos for celebrities (only if parsed successfully)
  useEffect(() => {
    if (!parsed) return;
    
    const { celebrities, celebrityIds } = parsed;
    const abortController = new AbortController();
    
    celebrities.forEach((celebrity, index) => {
      const celebrityId = celebrityIds?.[index];
      fetchCelebrityPhoto(celebrity, celebrityId, abortController.signal);
    });
    
    return () => abortController.abort();
  }, [parsed, fetchCelebrityPhoto]);

  if (!parsed) return <div className="text-gray-400">No path found.</div>;

  const { celebrities, titles, celebrityIds, titleIds } = parsed;

  return (
    // New outer wrapper to center the entire block of content
    <div className="flex justify-center w-full">
      {/* Inner container uses justify-start for crisp vertical alignment of wrapped rows */}
      <div className="flex flex-row flex-wrap justify-start items-start gap-y-4">
        {celebrities.map((celebrity, idx) => (
          <div key={celebrityIds?.[idx] || `celebrity-${idx}`} className="flex items-start">
            {/* --- Celebrity Block --- */}
            {/* This block now has a fixed width for consistency */}
            <div className="flex flex-col items-center gap-0.5 sm:gap-1 p-0.5 sm:p-1 text-white w-16 sm:w-20 md:w-24 lg:w-28">
              {celebrityPhotos[celebrity] ? (
                <Image src={celebrityPhotos[celebrity]} alt={celebrity} width={80} height={80} className="w-10 h-10 sm:w-14 sm:h-14 md:w-16 md:h-16 lg:w-18 lg:h-18 xl:w-20 xl:h-20 rounded-lg object-cover border-2 border-white" />
              ) : (
                <div className="w-10 h-10 sm:w-14 sm:h-14 md:w-16 md:h-16 lg:w-18 lg:h-18 xl:w-20 xl:h-20 rounded-lg bg-blue-800 flex items-center justify-center text-white font-bold border-2 border-white text-lg sm:text-xl md:text-2xl lg:text-3xl xl:text-4xl">
                  {celebrity.charAt(0)}
                </div>
              )}
              {celebrityIds?.[idx] ? (
                <a href={`https://www.imdb.com/name/${celebrityIds[idx]}/`} target="_blank" rel="noopener noreferrer" className="font-semibold text-[7px] sm:text-[8px] md:text-xs lg:text-sm xl:text-base text-center leading-tight break-words text-blue-400 hover:text-blue-300 underline">
                  {celebrity}
                </a>
              ) : (
                <span className="font-semibold text-[7px] sm:text-[8px] md:text-xs lg:text-sm xl:text-base text-center leading-tight break-words">{celebrity}</span>
              )}
            </div>

            {/* --- Connector Block (if not the last celebrity) --- */}
            {idx < celebrities.length - 1 && (
              // This block also has a fixed width, making all arrows equidistant
              <div className="flex flex-col items-center justify-start pt-2 w-12 sm:w-16 md:w-20 lg:w-24">
                <FaArrowsAltH className="text-base sm:text-lg md:text-xl lg:text-2xl text-white" />
                <div className="flex flex-col items-center mt-0.5">
                  {titleIds?.[idx] && titleIds[idx] !== "unknown" ? (
                    <a href={`https://www.imdb.com/title/${titleIds[idx]}/`} target="_blank" rel="noopener noreferrer" className="text-[8px] sm:text-[9px] md:text-[10px] lg:text-[11px] text-blue-400 hover:text-blue-300 text-center leading-tight break-words underline">
                      {titles[idx]}
                    </a>
                  ) : (
                    <span className="text-[8px] sm:text-[9px] md:text-[10px] lg:text-[11px] text-gray-300 text-center leading-tight break-words">{titles[idx]}</span>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}