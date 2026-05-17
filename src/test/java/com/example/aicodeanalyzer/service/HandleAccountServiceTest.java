package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.RepositoryTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandleAccountServiceTest {

    @Test
    void addHandleNormalizesPlatformAliasTrimsHandleAndNotes() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        HandleAccount saved = service.addHandle("Codeforces", "  tourist_01  ", "  has consent in class  ");

        assertEquals(platform.getPlatformId(), saved.getPlatformId());
        assertEquals("tourist_01", saved.getHandle());
        assertEquals("tourist_01", saved.getDisplayName());
        assertEquals("UNKNOWN", saved.getConsentStatus());
        assertEquals("has consent in class", saved.getNotes());
        assertTrue(saved.isActive());
    }

    @Test
    void addHandleRejectsInvalidHandleBeforeItCanReachDatabase() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        RepositoryTestSupport.seedPlatform(factory);
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addHandle("CODEFORCES", "abc'; DROP TABLE dbo.platforms;--", null)
        );

        assertTrue(exception.getMessage().contains("letters"));
        assertTrue(service.findAllHandles().isEmpty());
    }

    @Test
    void addHandleRejectsDuplicatePlatformAndHandleAtServiceLayer() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        RepositoryTestSupport.seedPlatform(factory);
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        service.addHandle("CODEFORCES", "duplicate_user", null);

        DatabaseException exception = assertThrows(
                DatabaseException.class,
                () -> service.addHandle("CODEFORCES", "duplicate_user", "second")
        );

        assertTrue(exception.getMessage().contains("already exists"));
        assertEquals(1, service.findAllHandles().size());
    }

    @Test
    void updateHandleAllowsKeepingSameHandleButRejectsCollisionWithAnotherHandle() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount first = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "first_user");
        RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "second_user");
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        HandleAccount unchangedName = service.updateHandle(first.getHandleId(), "Codeforces", " first_user ", "   ");
        assertEquals("first_user", unchangedName.getHandle());
        assertNull(unchangedName.getNotes());

        DatabaseException exception = assertThrows(
                DatabaseException.class,
                () -> service.updateHandle(first.getHandleId(), "CODEFORCES", "second_user", null)
        );

        assertTrue(exception.getMessage().contains("already exists"));
        List<HandleAccount> handles = service.findAllHandles();
        assertEquals(2, handles.size());
    }
}
