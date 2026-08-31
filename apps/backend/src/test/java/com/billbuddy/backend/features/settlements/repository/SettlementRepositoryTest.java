package com.billbuddy.backend.features.settlements.repository;

import com.billbuddy.backend.features.auth.model.User;
import com.billbuddy.backend.features.auth.repository.UserRepository;
import com.billbuddy.backend.features.groups.model.Group;
import com.billbuddy.backend.features.groups.repository.GroupRepository;
import com.billbuddy.backend.features.settlements.model.Settlement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        return userRepository.save(User.signupWithEmail(email, "hashed-password", "User", null));
    }

    private Group persistGroup(User creator) {
        return groupRepository.save(Group.create("Goa Trip", "desc", "INR", creator));
    }

    private Settlement persistSettlement(Group group, User paidBy, User paidTo, User createdBy, BigDecimal amount) {
        return settlementRepository.save(Settlement.create(group, paidBy, paidTo, createdBy, amount, "INR", null));
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludesSoftDeletedSettlement() {
        User admin = persistUser("admin@example.com");
        User member = persistUser("member@example.com");
        Group group = persistGroup(admin);
        Settlement settlement = persistSettlement(group, member, admin, member, new BigDecimal("50.00"));
        settlement.softDelete();
        settlementRepository.save(settlement);
        entityManager.flush();
        entityManager.clear();

        Optional<Settlement> result = settlementRepository.findByIdAndDeletedAtIsNull(settlement.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc_excludesDeletedAndOrdersByNewestFirst() throws InterruptedException {
        User admin = persistUser("admin@example.com");
        User member = persistUser("member@example.com");
        Group group = persistGroup(admin);

        Settlement first = persistSettlement(group, member, admin, member, new BigDecimal("10.00"));
        Thread.sleep(5); // ensure a distinct created_at ordering between inserts
        Settlement second = persistSettlement(group, member, admin, member, new BigDecimal("20.00"));

        Settlement deleted = persistSettlement(group, member, admin, member, new BigDecimal("30.00"));
        deleted.softDelete();
        settlementRepository.save(deleted);

        entityManager.flush();
        entityManager.clear();

        List<Settlement> result = settlementRepository.findByGroup_IdAndDeletedAtIsNullOrderByCreatedAtDesc(group.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(second.getId());
        assertThat(result.get(1).getId()).isEqualTo(first.getId());
    }

    @Test
    void findByGroup_IdAndDeletedAtIsNull_returnsAllActiveSettlementsForGroup() {
        User admin = persistUser("admin@example.com");
        User member = persistUser("member@example.com");
        Group group = persistGroup(admin);
        Group otherGroup = persistGroup(admin);

        persistSettlement(group, member, admin, member, new BigDecimal("10.00"));
        persistSettlement(group, member, admin, member, new BigDecimal("20.00"));
        persistSettlement(otherGroup, member, admin, member, new BigDecimal("99.00"));

        entityManager.flush();
        entityManager.clear();

        List<Settlement> result = settlementRepository.findByGroup_IdAndDeletedAtIsNull(group.getId());

        assertThat(result).hasSize(2);
    }
}
