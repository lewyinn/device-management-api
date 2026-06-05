import app from './src/index.js';
import database from './src/db/index.js';
import dotenv from 'dotenv';

dotenv.config({quiet: true});

database.sync({ alter: false }).then(() => {
    const port = process.env.PORT || 3000;
    app.listen(port, () => {
        console.log(`Server running on http://localhost:${port}`);
        console.log(`Swagger UI: http://localhost:${port}/api-docs`);
    });
});

export default app;
