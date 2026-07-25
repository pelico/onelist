# Application Summary for The Movie Database (TMDB) API

## Project Name
OneList - Self-hosted Personal Media Library

## Project Overview
OneList is an open-source, self-hosted personal media library management system designed for individual users to organize their personal movie and TV show collections. It automatically scrapes media metadata from TMDB and provides a beautiful web interface for browsing, searching, and managing personal video collections.

## How We Plan to Use the TMDB API

### 1. Media Metadata Retrieval
- **Movie Information**: Fetch detailed movie data including title, overview, release date, runtime, genres, ratings, production companies, and countries.
- **TV Show Information**: Retrieve TV show details including name, overview, first air date, number of seasons/episodes, genres, and ratings.
- **Cast & Crew**: Get credits information (actors, directors, writers) to enrich media details pages.
- **Person Information**: Fetch actor/director profiles including biography, birthday, and filmography.
- **Season & Episode Details**: Retrieve specific season and episode information for TV shows.

### 2. Image Retrieval
- **Poster Images**: Download movie and TV show poster art for display in the media library grid and detail pages.
- **Backdrop Images**: Fetch backdrop/background images for use in detail page headers.
- **Profile Images**: Retrieve person profile photos for cast and crew pages.
- **Still Images**: Get episode still frames for TV show episode listings.

### 3. Search Functionality
- Use TMDB search API to match user's local video files with the correct movie or TV show entries in the TMDB database.
- Support both movie search and TV search based on filename parsing.

### 4. Use Flow
1. User adds their media directories (local or cloud storage via Alist) to the system
2. System scans video files and extracts media names from filenames
3. System queries TMDB search API to find matching movies/TV shows
4. System fetches full metadata and images for matched items
5. Metadata and images are stored locally in a database and on disk
6. Users can browse, search, and play their media through the web interface

## Technical Implementation
- **Backend**: Go (Golang) with GORM for database operations
- **Frontend**: Vue 3 with Vite
- **Database**: SQLite / MySQL (configurable)
- **Image Caching**: Images are cached locally to reduce API calls
- **API Key Usage**: Each deployment uses its own API key configured by the end-user
- **Rate Limiting**: Implements sequential scraping with delays to respect TMDB rate limits

## Compliance with TMDB Terms
- **Non-commercial**: This is a free, open-source project for personal use only
- **Attribution**: We display TMDB attribution on all media detail pages as required
- **No Redistribution**: We do not redistribute TMDB data or images; each user's instance fetches data independently
- **Caching**: Images and metadata are cached locally for performance but are not redistributed
- **Rate Limits**: We implement reasonable request spacing to avoid overwhelming TMDB servers

## Target Audience
- Individual users who want to organize their personal media collections
- Tech enthusiasts who self-host their own services
- Users with legally obtained personal video collections

## API Call Volume Estimate
Per user instance:
- Initial library scan: 1-5 API calls per media item (search + details + credits + images)
- Daily updates: Minimal, only for newly added items
- Average user with 500 movies + 100 TV shows: ~3000-5000 total API calls during initial setup

## Contact
For any questions about this application, please reach out through the project's GitHub repository.

---

**Note**: This is a self-hosted application where each end-user deploys their own instance and uses their own TMDB API key. The application itself does not serve TMDB data to third parties.
