CREATE TABLE predictions
(
    id         SERIAL PRIMARY KEY,
    author_id  BIGINT NOT NULL,
    prediction TEXT   NOT NULL
);

CREATE TABLE votes
(
    user_id       BIGINT  NOT NULL,
    prediction_id INT     NOT NULL REFERENCES predictions (id),
    vote          BOOLEAN NOT NULL,
    PRIMARY KEY (user_id, prediction_id)
);

ALTER TABLE votes
    ADD CONSTRAINT fk_votes_prediction
        FOREIGN KEY (prediction_id) REFERENCES predictions(id) ON DELETE CASCADE;