package org.saturn.app.service;

import java.util.List;

public interface NoteService {
    void executeNotesPurge(String author, String trip);
    void executeListNotes(String author, String trip);
    void save(String trip, String note);
    List<String> getNotesByTrip(String trip);
    void clearNotesByTrip(String trip);
}
