package az.yolcu.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String phone;
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private int coinBalance;
    private boolean accessibility;
    private boolean trackingConsent = true;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected User() {}

    public User(String email, String passwordHash, String fullName, Role role, int coinBalance) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.coinBalance = coinBalance;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public int getCoinBalance() { return coinBalance; }
    public boolean isAccessibility() { return accessibility; }
    public boolean isTrackingConsent() { return trackingConsent; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
