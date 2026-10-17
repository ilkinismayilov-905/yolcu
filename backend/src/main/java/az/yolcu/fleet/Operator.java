package az.yolcu.fleet;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only view of operators (seeded). */
@Entity
@Table(name = "operators")
public class Operator {
    @Id
    private Long id;
    private String code;
    private String name;
    private String routes;

    protected Operator() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getRoutes() { return routes; }
}
