export const errorHandler = (err, req, res, next) => {
    console.error(err);
    if (err.name === 'SequelizeValidationError') {
        return res.status(400).json({
            error: 'Validation failed',
            details: err.errors.map(e => e.message)
        });
    }
    if (err.name === 'SequelizeUniqueConstraintError') {
        return res.status(409).json({
            error: 'Duplicate telemetry timestamp',
            details: 'Telemetry timestamp must be unique per device'
        });
    }
    res.status(500).json({ error: 'Internal server error' });
};
