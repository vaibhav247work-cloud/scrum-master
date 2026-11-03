INSERT INTO feature_definition (feature_key, display_name, status) VALUES
    ('PROJECTS', 'Projects', 'ACTIVE'),
    ('SCRUM', 'Scrum and Sprints', 'ACTIVE'),
    ('WORK_MANAGEMENT', 'Work Management', 'ACTIVE'),
    ('RESOURCE_MANAGEMENT', 'Resource Management', 'ACTIVE'),
    ('TIME_TRACKING', 'Time Tracking', 'ACTIVE'),
    ('RELEASE_MANAGEMENT', 'Release Management', 'ACTIVE'),
    ('REPORTING', 'Reporting and Control Tower', 'ACTIVE'),
    ('NOTIFICATIONS', 'Notifications and Collaboration', 'ACTIVE'),
    ('INTEGRATIONS', 'External Integrations', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status);
