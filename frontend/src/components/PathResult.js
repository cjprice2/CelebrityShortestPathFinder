import { FaArrowsAltH } from "react-icons/fa";
import { useState, useEffect } from "react";

function parseResult(result) {
  // Split the result into lines
  const lines = result.trim().split("\n");
  if (lines.length < 2) return null;
  const startIdLine = lines.find(line => line.startsWith("START_ID:"));
  const endIdLine = lines.find(line => line.startsWith("END_ID:"));
  const startId = startIdLine ? startIdLine.replace("START_ID:", "").trim() : null;
  const endId = endIdLine ? endIdLine.replace("END_ID:", "").trim() : null;

  // Find the ACTOR_IDS and MOVIE_IDS lines
  const actorIdsLine = lines.find(line => line.startsWith("ACTOR_IDS:"));
  const actorIds = actorIdsLine ? actorIdsLine.replace("ACTOR_IDS:", "").split(",").filter(id => id) : [];

  const movieIdsLine = lines.find(line => line.startsWith("MOVIE_IDS:"));
  const movieIds = movieIdsLine ? movieIdsLine.replace("MOVIE_IDS:", "").split(",").filter(id => id) : [];

  // First non-marker line should be the actors
  const firstContentLineIdx = lines.findIndex(l => !l.startsWith("START_ID:") && !l.startsWith("END_ID:"));
  const actors = (firstContentLineIdx >= 0 ? lines[firstContentLineIdx] : "").split(" -> ");

  // Structured titles only; no legacy fallback
  const movieTitlesLine = lines.find(line => line.startsWith("MOVIE_TITLES:"));
  const movies = movieTitlesLine ? movieTitlesLine.replace("MOVIE_TITLES:", "").split(",").filter(t => t) : [];

  // Normalize orientation using explicit endpoints if provided
  if (startId && actorIds.length > 0 && actorIds[0] !== startId) {
    actors.reverse();
    actorIds.reverse();
    movies.reverse();
    movieIds.reverse();
  }

  return { actors, movies, actorIds, movieIds };
}

export default function PathResult({ result }) {
  const [actorPhotos, setActorPhotos] = useState({});
  const parsed = parseResult(result);

  useEffect(() => {
    setActorPhotos({});
  }, [result]);

  if (!parsed) return <div className="text-gray-400">No path found.</div>;

  const { actors, movies, actorIds, movieIds } = parsed;

  const fetchActorPhoto = async (actorName, actorId, signal) => {
    if (actorPhotos[actorName] || !actorId) return;
    try {
      const url = `/api/actor-photo?actorId=${encodeURIComponent(actorId)}&actorName=${encodeURIComponent(actorName)}`;
      const response = await fetch(url, { signal });
      if (!response.ok) return;
      const data = await response.json();
      if (data.photoUrl) {
        setActorPhotos(prev => ({ ...prev, [actorName]: data.photoUrl }));
      }
    } catch (error) {
      if (error.name !== 'AbortError') console.error("Error fetching actor photo:", error);
    }
  };

  useEffect(() => {
    const abortController = new AbortController();
    actors.forEach((actor, index) => {
      const actorId = actorIds?.[index];
      fetchActorPhoto(actor, actorId, abortController.signal);
    });
    return () => abortController.abort();
  }, [actors.join(','), actorIds.join(',')]);

  return (
    // New outer wrapper to center the entire block of content
    <div className="flex justify-center w-full">
      {/* Inner container uses justify-start for crisp vertical alignment of wrapped rows */}
      <div className="flex flex-row flex-wrap justify-start items-start gap-y-4">
        {actors.map((actor, idx) => (
          <div key={actorIds?.[idx] || `actor-${idx}`} className="flex items-start">
            {/* --- Actor Block --- */}
            {/* This block now has a fixed width for consistency */}
            <div className="flex flex-col items-center gap-0.5 sm:gap-1 p-0.5 sm:p-1 text-white w-16 sm:w-20 md:w-24 lg:w-28">
              {actorPhotos[actor] ? (
                <img src={actorPhotos[actor]} alt={actor} className="w-10 h-10 sm:w-14 sm:h-14 md:w-16 md:h-16 lg:w-18 lg:h-18 xl:w-20 xl:h-20 rounded-lg object-cover border-2 border-white" />
              ) : (
                <div className="w-10 h-10 sm:w-14 sm:h-14 md:w-16 md:h-16 lg:w-18 lg:h-18 xl:w-20 xl:h-20 rounded-lg bg-blue-800 flex items-center justify-center text-white font-bold border-2 border-white text-lg sm:text-xl md:text-2xl lg:text-3xl xl:text-4xl">
                  {actor.charAt(0)}
                </div>
              )}
              {actorIds?.[idx] ? (
                <a href={`https://www.imdb.com/name/${actorIds[idx]}/`} target="_blank" rel="noopener noreferrer" className="font-semibold text-[7px] sm:text-[8px] md:text-xs lg:text-sm xl:text-base text-center leading-tight break-words text-blue-400 hover:text-blue-300 underline">
                  {actor}
                </a>
              ) : (
                <span className="font-semibold text-[7px] sm:text-[8px] md:text-xs lg:text-sm xl:text-base text-center leading-tight break-words">{actor}</span>
              )}
            </div>

            {/* --- Connector Block (if not the last actor) --- */}
            {idx < actors.length - 1 && (
              // This block also has a fixed width, making all arrows equidistant
              <div className="flex flex-col items-center justify-start pt-2 w-12 sm:w-16 md:w-20 lg:w-24">
                <FaArrowsAltH className="text-base sm:text-lg md:text-xl lg:text-2xl text-white" />
                <div className="flex flex-col items-center mt-0.5">
                  {movieIds?.[idx] && movieIds[idx] !== "unknown" ? (
                    <a href={`https://www.imdb.com/title/${movieIds[idx]}/`} target="_blank" rel="noopener noreferrer" className="text-[8px] sm:text-[9px] md:text-[10px] lg:text-[11px] text-blue-400 hover:text-blue-300 text-center leading-tight break-words underline">
                      {movies[idx]}
                    </a>
                  ) : (
                    <span className="text-[8px] sm:text-[9px] md:text-[10px] lg:text-[11px] text-gray-300 text-center leading-tight break-words">{movies[idx]}</span>
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