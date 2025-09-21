import { FaFilm, FaSearch, FaRoute } from "react-icons/fa";

export default function Header() {
  return (
    <header className="bg-gray-900 text-gray-400 border-b border-gray-700">
      <div className="container mx-auto px-6 py-3">
        <div className="flex flex-col items-center text-center">
          {/* Main Title with Icons */}
          <div className="flex items-center gap-3 mb-2">
            <FaFilm className="text-2xl text-blue-900" />
            <h1 className="text-base sm:text-xl md:text-2xl lg:text-3xl font-bold text-white whitespace-nowrap">
              Celebrity Shortest Path Finder
            </h1>
            <FaRoute className="text-2xl text-blue-900" />
          </div>
          
          {/* Subtitle */}
          <p className="text-sm text-gray-300 max-w-2xl leading-relaxed">
            Discover connections between celebrities through their shared titles using bidirectional BFS!
          </p>
        </div>
      </div>
    </header>
  );
}
