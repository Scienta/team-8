package no.scienta.cfp;

/**
 * One attendee's registration for a workshop, as the API shows it. The email is as the attendee typed it.
 */
public class Registration {

    public final long id;
    public final long workshopId;
    public final String name;
    public final String email;

    public Registration(long id, long workshopId, String name, String email) {
        this.id = id;
        this.workshopId = workshopId;
        this.name = name;
        this.email = email;
    }
}
